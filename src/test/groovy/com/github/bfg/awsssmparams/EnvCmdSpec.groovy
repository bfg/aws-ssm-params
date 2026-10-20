package com.github.bfg.awsssmparams


import groovy.util.logging.Slf4j

@Slf4j
class EnvCmdSpec extends CmdSpecBase {
  String subCommand() {
    "env"
  }

  def "[#arg] should display version"() {
    when:
    def (status, stdout, stderr) = runCmd([arg])

    then:
    status == 0
    stdout.lines().size()== 1
    stderr.isEmpty()

    with(stdout) {
      !it.isBlank()
      def line = it.trim()
          line.endsWith('-SNAPSHOT') || line.matches('^\\d+\\..*')
    }

    where:
    arg << ['-V', '--version']
  }

  def "[#arg] should display help"() {
    when:
    def (status, stdout, stderr) = runCmd([arg])

    then:
    status == 0
    stdout.lines().size()>= 15
    stdout.contains('Usage: ')
    stderr.isEmpty()

    where:
    arg << ['-h', '--help']
  }

  def "[#arg] should respond with exit status 1 in case of a bad argument"() {
    when:
    def (status, stdout, stderr) = runCmd(['--non-existing-arg=ffff'])

    then:
    status >= 1
    stdout.isBlank()
    stderr.contains("Unknown option: '--non-existing")
  }


  def '[fmt: #fmt] should not produce any output for non-existing paths'() {
    when:
    def (status, stdout, stderr) = runCmd(['/this-ssm/path/does/not-exist']) {
      if (!fmt.isBlank()) it.addAll(['-F', fmt])
    }

    then:
    status == 0
    stderr.isEmpty()
    stdout.isEmpty()

    where:
    fmt << ['', 'exports', 'dotenv']
  }

  def '[fmt: exports] should fetch parseable json [1]'() {
    when:
    def (status, stdout, stderr) = runCmd(['/test/shell-injection/'])

    then:
    status == 0
    stderr.isEmpty()
    !stdout.isBlank()

    when: "create a script, run it and parse result with jq"
    (status, stdout, stderr) = shellSourceOutput(stdout, """
# \$J should contain a json, let's check if that's the case, parse it with jq
echo "\$J" | jq .
""")

    then:
    status == 0
    stderr.isEmpty()

    // stdout should contain jq(1) pretty-printed json object extracted from /test2/json-values/a
    stdout == """{
  "app": "trololo-app",
  "name": "trololo-app",
  "type": "Load Balanced Web Service "
}
"""
  }

  def '[fmt: exports] should fetch parseable json [2]'() {
    when:
    def (status, stdout, stderr) = runCmd(['/test/', '--recursive'])

    then:
    status == 0
    stderr.isEmpty()
    !stdout.isBlank()

    when: "create a script, run it and parse result with jq"
     (status, stdout, stderr) = shellSourceOutput(stdout, """
# \$J should contain a json, let's check if that's the case, parse it with jq
echo "\$SHELL_INJECTION_J" | jq .
""")

    then:
    status == 0
    stderr.isEmpty()

    // stdout should contain jq(1) pretty-printed json object extracted from /test2/json-values/a
    stdout == """{
  "app": "trololo-app",
  "name": "trololo-app",
  "type": "Load Balanced Web Service "
}
"""
  }

  def '[fmt: exports, #varName] should produce shell evaluatable output and should set correct value'() {
    when:
    // - exports should be default format
    // - all values should be trimmed
    def (status, stdout, stderr) = runCmd(['/test/shell-injection/'])

    then:
    status == 0
    stderr.isEmpty()
    !stdout.isBlank()

    when:
    (status, stdout, stderr) = shellSourceOutput(stdout, """
echo "\$$varName"
""")

    then:
    status == 0
    stderr.isEmpty()
    //stdout.lines().filter { it.startsWith('export \'') }.count() == 15
    stdout == expectedValue + "\n" // expected value + newline

    where:
    varName | expectedValue
    'A'     | 'a=`ls /etc`'     // should not run sub-shell assignment - 1
    'B'     | 'b=$(ls /etc)'    // should not run sub-shell assignment - 2
    'C'     | '"x'
    'D'     | 'x"'
    'E'     | '"x"'
    'F'     | '\'x"'
    'G'     | '"\'x'
    'H'     | '$HOME' // should not evaluate shell vars
    'I'     | 'eval x=$((1 + 1))' // should not evaluate expressions
    'J'     | '{"app":"trololo-app","name":"trololo-app","type":"Load Balanced Web Service "}' // should retain json strings
    'K'     | "foo\n  bar" // should retain newlines and spaces
    'L'     | "foo\n\t \n \t bar" // should retain tabs, spaces, newlines
    'M'     | '`ls /etc`'   // should not run external commands
    'N'     | '$(ls /etc)' // should not run external commands
    'O'     | 'my_super_function' // should not shell functions
  }

  def '[fmt: dotenv, #varName] should produce shell evaluatable output and should set correct value'() {
    when:
    def (status, stdout, stderr) = runCmd(['/test/shell-injection/', '--format=dotenv'])

    then:
    status == 0
    !stdout.isBlank()
    stderr.isEmpty()

    when:
    (status, stdout, stderr) = shellSourceOutput(stdout, """
echo "\$$varName"
""")

    then:
    status == 0
    stderr.isEmpty()
    stdout.lines().filter { it.startsWith('export ') }.count() == 0
    stdout == expectedValue + "\n" // expected value + newline

    where:
    varName | expectedValue
    'A'     | 'a=`ls /etc`'     // should not run sub-shell assignment - 1
    'B'     | 'b=$(ls /etc)'    // should not run sub-shell assignment - 2
    'C'     | '"x'
    'D'     | 'x"'
    'E'     | '"x"'
    'F'     | '\'x"'
    'G'     | '"\'x'
    'H'     | '$HOME' // should not evaluate shell vars
    'I'     | 'eval x=$((1 + 1))' // should not evaluate expressions
    'J'     | '{"app":"trololo-app","name":"trololo-app","type":"Load Balanced Web Service "}' // should retain json strings
    'K'     | "foo\n  bar" // should retain newlines and spaces
    'L'     | "foo\n\t \n \t bar" // should retain tabs, spaces, newlines
    'M'     | '`ls /etc`'   // should not run external commands
    'N'     | '$(ls /etc)' // should not run external commands
    'O'     | 'my_super_function' // should not shell functions
  }
}
