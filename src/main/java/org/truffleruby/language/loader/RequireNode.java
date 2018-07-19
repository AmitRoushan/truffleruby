/*
 * Copyright (c) 2016, 2018 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.loader;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import org.jcodings.specific.UTF8Encoding;
import org.truffleruby.RubyLogger;
import org.truffleruby.RubyLanguage;
import org.truffleruby.core.rope.CodeRange;
import org.truffleruby.core.string.StringNodes;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.WarningNode;
import org.truffleruby.language.control.JavaException;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.CallDispatchHeadNode;
import org.truffleruby.language.methods.DeclarationContext;
import org.truffleruby.parser.ParserContext;
import org.truffleruby.parser.RubySource;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

@NodeChild("feature")
public abstract class RequireNode extends RubyNode {

    @Child private IndirectCallNode callNode = IndirectCallNode.create();
    @Child private CallDispatchHeadNode isInLoadedFeatures = CallDispatchHeadNode.createOnSelf();
    @Child private CallDispatchHeadNode addToLoadedFeatures = CallDispatchHeadNode.createOnSelf();

    @Child private Node readNode = Message.READ.createNode();
    @Child private Node isExecutableNode = Message.IS_EXECUTABLE.createNode();
    @Child private Node executeNode = Message.createExecute(0).createNode();
    @Child private WarningNode warningNode;

    public static RequireNode create() {
        return RequireNodeGen.create(null);
    }

    public abstract boolean executeRequire(String feature);

    @Specialization
    protected boolean require(String feature,
            @Cached("create()") BranchProfile notFoundProfile,
            @Cached("createBinaryProfile()") ConditionProfile isLoadedProfile,
            @Cached("create()") StringNodes.MakeStringNode makeStringNode) {
        final String expandedPath = getContext().getFeatureLoader().findFeature(feature);

        if (expandedPath == null) {
            notFoundProfile.enter();
            throw new RaiseException(getContext(), getContext().getCoreExceptions().loadErrorCannotLoad(feature, this));
        }

        final DynamicObject pathString = makeStringNode.executeMake(expandedPath, UTF8Encoding.INSTANCE, CodeRange.CR_UNKNOWN);

        if (isLoadedProfile.profile(isFeatureLoaded(pathString))) {
            return false;
        } else {
            return doRequire(feature, expandedPath, pathString);
        }
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    private boolean doRequire(String feature, String expandedPathRaw, DynamicObject pathString) {
        final FeatureLoader featureLoader = getContext().getFeatureLoader();
        final ReentrantLockFreeingMap<String> fileLocks = featureLoader.getFileLocks();
        final String expandedPath = expandedPathRaw.intern();

        while (true) {
            final ReentrantLock lock = fileLocks.get(expandedPath);

            if (lock.isHeldByCurrentThread()) {
                warnCircularRequire(expandedPath);
                return false;
            }

            if (!fileLocks.lock(this, getContext().getThreadManager(), expandedPath, lock)) {
                continue;
            }

            try {
                if (isFeatureLoaded(pathString)) {
                    return false;
                }

                final RubySource source;
                try {
                    source = getContext().getSourceLoader().load(expandedPath);
                } catch (IOException e) {
                    return false;
                }

                final String mimeType = getSourceMimeType(source.getSource());

                if (RubyLanguage.MIME_TYPE.equals(mimeType)) {
                    final RubyRootNode rootNode = getContext().getCodeLoader().parse(
                            source,
                            ParserContext.TOP_LEVEL,
                            null,
                            true,
                            this);

                    final CodeLoader.DeferredCall deferredCall = getContext().getCodeLoader().prepareExecute(
                            ParserContext.TOP_LEVEL,
                            DeclarationContext.topLevel(getContext()),
                            rootNode,
                            null,
                            coreLibrary().getMainObject());

                    deferredCall.call(callNode);
                } else if (RubyLanguage.CEXT_MIME_TYPE.equals(mimeType)) {
                    requireCExtension(feature, expandedPath);
                } else {
                    throw new RaiseException(getContext(), mimeTypeNotFound(expandedPath, mimeType));
                }

                addToLoadedFeatures(pathString);

                return true;
            } finally {
                fileLocks.unlock(expandedPath, lock);
            }
        }
    }

    @TruffleBoundary
    private DynamicObject mimeTypeNotFound(String expandedPath, String mimeType) {
        if (expandedPath.toLowerCase(Locale.ENGLISH).endsWith(".su")) {
            return coreExceptions().notImplementedError("cext support is not available to load " + expandedPath, this);
        } else {
            return coreExceptions().argumentError("unknown language " + mimeType + " for " + expandedPath, this);
        }
    }

    @TruffleBoundary
    private void requireCExtension(String feature, String expandedPath) {
        final FeatureLoader featureLoader = getContext().getFeatureLoader();

        final List<TruffleObject> libraries;

        try {
            featureLoader.ensureCExtImplementationLoaded(feature, this);

            if (getContext().getOptions().CEXTS_LOG_LOAD) {
                RubyLogger.LOGGER.info(String.format("loading cext module %s (requested as %s)", expandedPath, feature));
            }

            libraries = featureLoader.loadCExtLibrary(expandedPath);
        } catch (Exception e) {
            handleCExtensionException(feature, e);
            throw e;
        }

        final String initFunctionName = "Init_" + getBaseName(expandedPath);

        final TruffleObject initFunction = findFunctionInLibraries(libraries, initFunctionName, expandedPath);

        if (!ForeignAccess.sendIsExecutable(isExecutableNode, initFunction)) {
            throw new RaiseException(getContext(), coreExceptions().loadError(initFunctionName + "() is not executable", expandedPath, null));
        }

        try {
            ForeignAccess.sendExecute(executeNode, initFunction);
        } catch (InteropException e) {
            throw new JavaException(e);
        }
    }

    public TruffleObject findFunctionInLibraries(List<TruffleObject> libraries, String functionName, String path) {
        Object initObject = null;

        for (TruffleObject library : libraries) {
            try {
                initObject = ForeignAccess.sendRead(readNode, library, functionName);
            } catch (UnknownIdentifierException e) {
                // TODO CS 18-Mar-18 it's not ideal that we're catching and throwing an exception when we don't find Init_ in each file
                continue;
            } catch (UnsupportedMessageException e) {
                throw new JavaException(e);
            }

            if (initObject != null) {
                break;
            }
        }

        if (initObject == null) {
            throw new RaiseException(getContext(), coreExceptions().loadError(String.format("%s() not found", functionName), path, null));
        }

        if (!(initObject instanceof TruffleObject)) {
            throw new RaiseException(getContext(), coreExceptions().loadError(String.format("%s() was a %s rather than a TruffleObject", functionName, initObject.getClass().getSimpleName()), path, null));
        }

        return (TruffleObject) initObject;
    }

    @TruffleBoundary
    private void handleCExtensionException(String feature, Exception e) {
        final UnsatisfiedLinkError linkErrorException = searchForException(UnsatisfiedLinkError.class, e);
        if (linkErrorException != null) {
            final String linkError = linkErrorException.getMessage();

            if (getContext().getOptions().CEXTS_LOG_LOAD) {
                RubyLogger.LOGGER.info("unsatisfied link error " + linkError);
            }

            final String message;

            if (linkError.contains("libc++.")) {
                message = String.format("%s (%s)", "you may need to install LLVM and libc++ - see https://github.com/oracle/truffleruby/blob/master/doc/user/installing-llvm.md", linkError);
            } else if (linkError.contains("libc++abi.")) {
                message = String.format("%s (%s)", "you may need to install LLVM and libc++abi - see https://github.com/oracle/truffleruby/blob/master/doc/user/installing-llvm.md", linkError);
            } else if (feature.equals("openssl.so")) {
                message = String.format("%s (%s)", "you may need to install the system OpenSSL library libssl - see https://github.com/oracle/truffleruby/blob/master/doc/user/installing-libssl.md", linkError);
            } else {
                message = linkError;
            }

            throw new RaiseException(getContext(), getContext().getCoreExceptions().runtimeError(message, this));
        }

        final Throwable linkerException = searchForException("LLVMLinkerException", e);
        if (linkerException != null) {
            final String linkError = linkerException.getMessage();
            final String message;
            final String home = getContext().getRubyHome();
            final String postInstallHook = (home != null ? home + "/" : "") + "lib/truffle/post_install_hook.sh";

            // Mismatches between the libssl compiled against and the libssl used at runtime (typically on a different machine)
            if (// These are comments and not part of the error message because they are guesses at best and could be confusing
                    // the system libssl is configured without SSLv2 support, such as on Ubuntu 16.04
                    linkError.contains("SSLv2_method cannot be found") ||
                    // Compiled against 1.0.2, newer libssl used
                    linkError.contains("SSLv23_method cannot be found") ||
                    // Compiled against 1.1.0, older libssl used
                    linkError.contains("TLS_method cannot be found")) {
                message = String.format("%s (%s)",
                        "the OpenSSL C extension was compiled against a different libssl than the one used on this system - recompile by running " + postInstallHook,
                        linkError);
            } else {
                message = linkError;
            }

            throw new RaiseException(getContext(), getContext().getCoreExceptions().runtimeError(message, this));
        }
    }

    private <T extends Throwable> T searchForException(Class<T> exceptionClass, Throwable exception) {
        while (exception != null) {
            if (exceptionClass.isInstance(exception)) {
                return exceptionClass.cast(exception);
            }
            exception = exception.getCause();
        }

        return null;
    }

    private Throwable searchForException(String exceptionClass, Throwable exception) {
        while (exception != null) {
            if (exception.getClass().getSimpleName().equals(exceptionClass)) {
                return exception;
            }
            exception = exception.getCause();
        }

        return null;
    }

    @TruffleBoundary
    private String getSourceMimeType(Source source) {
        return source.getMimeType();
    }

    @TruffleBoundary
    private String getBaseName(String path) {
        final String name = new File(path).getName();
        final int firstDot = name.indexOf('.');
        if (firstDot == -1) {
            return name;
        } else {
            return name.substring(0, firstDot);
        }
    }

    public boolean isFeatureLoaded(DynamicObject feature) {
        final DynamicObject loadedFeatures = getContext().getCoreLibrary().getLoadedFeatures();
        synchronized (getContext().getFeatureLoader().getLoadedFeaturesLock()) {
            return isInLoadedFeatures.callBoolean(null, loadedFeatures, "include?", feature);
        }
    }

    private void addToLoadedFeatures(DynamicObject feature) {
        final DynamicObject loadedFeatures = coreLibrary().getLoadedFeatures();
        synchronized (getContext().getFeatureLoader().getLoadedFeaturesLock()) {
            addToLoadedFeatures.call(null, loadedFeatures, "<<", feature);
        }
    }

    private void warnCircularRequire(String path) {
        if (warningNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            warningNode = insert(new WarningNode());
        }

        final SourceSection sourceSection = getContext().getCallStack().getTopMostUserSourceSection();
        warningNode.warningMessage(sourceSection, "loading in progress, circular require considered harmful - " + path);
    }

}
