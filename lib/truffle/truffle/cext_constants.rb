# From tool/generate-cext-constants.rb

module Truffle::CExt
  def Qundef
    Truffle::UNDEFINED
  end

  def Qtrue
    true
  end

  def Qfalse
    false
  end

  def Qnil
    nil
  end

  def rb_cArray
    Array
  end

  def rb_cClass
    Class
  end

  def rb_mComparable
    Comparable
  end

  def rb_cData
    Data
  end

  def rb_cEncoding
    Encoding
  end

  def rb_mEnumerable
    Enumerable
  end

  def rb_cFalseClass
    FalseClass
  end

  def rb_cFile
    File
  end

  def rb_cFloat
    Float
  end

  def rb_cHash
    Hash
  end

  def rb_cInteger
    Integer
  end

  def rb_cIO
    IO
  end

  def rb_mKernel
    Kernel
  end

  def rb_cMatch
    MatchData
  end

  def rb_cModule
    Module
  end

  def rb_cNilClass
    NilClass
  end

  def rb_cNumeric
    Numeric
  end

  def rb_cObject
    Object
  end

  def rb_cRange
    Range
  end

  def rb_cRegexp
    Regexp
  end

  def rb_cString
    String
  end

  def rb_cStruct
    Struct
  end

  def rb_cSymbol
    Symbol
  end

  def rb_cTime
    Time
  end

  def rb_cThread
    Thread
  end

  def rb_cTrueClass
    TrueClass
  end

  def rb_cProc
    Proc
  end

  def rb_cMethod
    Method
  end

  def rb_cDir
    Dir
  end

  def rb_eArgError
    ArgumentError
  end

  def rb_eEOFError
    EOFError
  end

  def rb_mErrno
    Errno
  end

  def rb_eException
    Exception
  end

  def rb_eFloatDomainError
    FloatDomainError
  end

  def rb_eIndexError
    IndexError
  end

  def rb_eInterrupt
    Interrupt
  end

  def rb_eIOError
    IOError
  end

  def rb_eKeyError
    KeyError
  end

  def rb_eLoadError
    LoadError
  end

  def rb_eLocalJumpError
    LocalJumpError
  end

  def rb_eMathDomainError
    Errno::EDOM
  end

  def rb_eEncCompatError
    Encoding::CompatibilityError
  end

  def rb_eNameError
    NameError
  end

  def rb_eNoMemError
    NoMemoryError
  end

  def rb_eNoMethodError
    NoMethodError
  end

  def rb_eNotImpError
    NotImplementedError
  end

  def rb_eRangeError
    RangeError
  end

  def rb_eRegexpError
    RegexpError
  end

  def rb_eRuntimeError
    RuntimeError
  end

  def rb_eScriptError
    ScriptError
  end

  def rb_eSecurityError
    SecurityError
  end

  def rb_eSignal
    SignalException
  end

  def rb_eStandardError
    StandardError
  end

  def rb_eSyntaxError
    SyntaxError
  end

  def rb_eSystemCallError
    SystemCallError
  end

  def rb_eSystemExit
    SystemExit
  end

  def rb_eSysStackError
    SystemStackError
  end

  def rb_eTypeError
    TypeError
  end

  def rb_eThreadError
    ThreadError
  end

  def rb_mWaitReadable
    IO::WaitReadable
  end

  def rb_mWaitWritable
    IO::WaitWritable
  end

  def rb_eZeroDivError
    ZeroDivisionError
  end

  def rb_stdin
    $stdin
  end

  def rb_stdout
    $stdout
  end

  def rb_stderr
    $stderr
  end

  def rb_output_fs
    $,
  end

  def rb_rs
    $/
  end

  def rb_output_rs
    $\
  end

  def rb_default_rs
    "\n"
  end

end
