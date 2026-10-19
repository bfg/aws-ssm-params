#!/bin/sh
set -eu

add_parameter() {
  local name="$1"; shift
  local value="$1"; shift

  local opts="--type String"
  test -n "$*" && opts="--type SecureString"

  awslocal --region=us-east-1 ssm put-parameter --overwrite $opts --name "$name" --value "$value"
  echo "added SSM parameter $name -> $value"
}

add_parameter /test/alpha/duplicate     'alpha-duplicate'
add_parameter /test/alpha/display_name  ' Alpha Display Name '
add_parameter /test/alpha/token         'alpha-token' 1

add_parameter /test/beta/duplicate      'beta-duplicate'
add_parameter /test/beta/endpoint       'beta-endpoint'
add_parameter /test/beta/password       'beta-password' 1

add_parameter /test/gamma/enabled       'gamma-enabled'
add_parameter /test/gamma/api_key       'gamma-api_key' 1
add_parameter /test/gamma/foo--.bar.BaZ 'gamma-foo-bar-baz' 1
add_parameter /test/gamma/0baz          'gamma-zero-baz' 1

add_parameter /test/shell-injection/a    'a=`ls /etc` '
add_parameter /test/shell-injection/b    'b=$(ls /etc) '
add_parameter /test/shell-injection/c    '"x '
add_parameter /test/shell-injection/d    'x" '
add_parameter /test/shell-injection/e    '"x" '
add_parameter /test/shell-injection/f    "'x\" "
add_parameter /test/shell-injection/g    "\"'x "
add_parameter /test/shell-injection/h    '$HOME '
add_parameter /test/shell-injection/i    'eval x=$((1 + 1)) '
add_parameter /test/shell-injection/j    ' {"app":"trololo-app","name":"trololo-app","type":"Load Balanced Web Service "} '
add_parameter /test/shell-injection/k    "foo\n  bar"
add_parameter /test/shell-injection/l    "foo\n\t \n \t bar"
add_parameter /test/shell-injection/m    '`ls /etc` '
add_parameter /test/shell-injection/n    '$(ls /etc) '
add_parameter /test/shell-injection/o    'my_super_function '

# vim:shiftwidth=2 softtabstop=2 expandtab
# EOF
