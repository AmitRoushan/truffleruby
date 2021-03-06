# Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative 'common_patches'

class PumaPatches < CommonPatches

  PUMA_HTTP_PARSER_FREE = <<-EOF
void HttpParser_free(puma_parser* hp) {
  TRACE();

  if(hp) {
    rb_tr_release_handle(hp->request);
    rb_tr_release_handle(hp->body);
    xfree(hp);
  }
}
EOF

  PATCHES = {
    gem: 'puma_http11',
    patches: {
      'http11_parser.c' => [
        {
          match: /parser->(\w+) = Qnil;/,
          replacement: 'parser->\1 = rb_tr_handle_for_managed(Qnil);'
        }
      ],
      'puma_http11.c' => [
        # Handles for VALUEs in a static global struct array
        {
          match: /(define\b.*?), Qnil \}/,
          replacement: '\1, NULL }'
        },
        {
          match: /cf->value = (.*?);/,
          replacement: 'cf->value = rb_tr_handle_for_managed(\1);'
        },
        {
          match: /return found \? found->value : Qnil;/,
          replacement: 'return found ? rb_tr_managed_from_handle(found->value) : Qnil;'
        },
        {
          match: /return cf->value;/,
          replacement: 'return rb_tr_managed_from_handle(cf->value);'
        },
        # Handles for puma_parser->request and puma_parser->body
        {
          match: /void HttpParser_free\(void \*data\) {.*?}.*?}/m,
          replacement: PUMA_HTTP_PARSER_FREE
        },
        {
          match: /\(hp->request\b/,
          replacement: '(rb_tr_managed_from_handle(hp->request)'
        },
        {
          match: /(\w+)->request = (.+?);/,
          replacement: '\1->request = rb_tr_handle_for_managed(\2);'
        },
        {
          match: /return http->body;/,
          replacement: 'return rb_tr_managed_from_handle(http->body);'
        },
        {
          match: /(\w+)->body = (.+?);/,
          replacement: '\1->body = rb_tr_handle_for_managed(\2);'
        }
      ]
    }
  }
end
