package com.github.bfg.awsssmparamsenv


import groovy.util.logging.Slf4j
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@Slf4j
class MainSpec extends Specification {
  PrintStream out = new MyPrintStream()

  def setup() {
    Main.output = out.clear()
  }

  def cleanup() {
    Main.output = System.out
  }

  def "[#arg] should display version"() {
    when:
    def res = Main.runMain(arg)
    def lines = out.lines()

    then:
    res == 0
    lines.size() == 1

    with(lines.first()) {
      !it.isBlank()
      it.endsWith('-SNAPSHOT') || it.matches('^\\d+\\..*')
    }

    where:
    arg << ['-V', '--version']
  }

  def "[#arg] should display help"() {
    when:
    def res = Main.runMain(arg)
    def lines = out.lines()

    then:
    res == 0
    lines.size() > 40

    with(lines.first()) {
      !it.isBlank()
      it.contains('Usage: ')
    }

    where:
    arg << ['-h', '--help']
  }

  def "[#arg] should respond with exit status 1 in case of a bad argument"() {
    given:
    def badArg = '--non-existing-arg=ffff'
    when:
    def res = Main.runMain(badArg)

    then:
    res >= 1
    out.toString().isBlank()
  }


  def '[fmt: #fmt] should not produce any output for non-existing paths'() {
    when:
    def (res, text) = runMain(['/this-ssm/path/does/not-exist']) {
      if (!fmt.isBlank()) it.addAll(['-F', fmt])
    }

    then:
    res == 0
    text.isEmpty()

    where:
    fmt << ['', 'exports', 'dotenv']
  }

  def '[fmt: exports] should fetch parseable json [1]'() {
    when:
    def (res, text) = runMain(['/test/shell-injection/'])

    then:
    res == 0
    !text.isBlank()

    when: "create a script, run it and parse result with jq"
    def (exitStatus, stdout, stderr) = writeAndRunShellScript(text, """
# \$J should contain a json, let's check if that's the case, parse it with jq
echo "\$J" | jq .
""")

    then:
    exitStatus == 0
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
    def (res, text) = runMain(['/test/', '--recursive'])

    then:
    res == 0
    !text.isBlank()

    when: "create a script, run it and parse result with jq"
    def (exitStatus, stdout, stderr) = writeAndRunShellScript(text, """
# \$J should contain a json, let's check if that's the case, parse it with jq
echo "\$SHELL_INJECTION_J" | jq .
""")

    then:
    exitStatus == 0
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
    def (res, text) = runMain(['/test/shell-injection/'])

    then:
    res == 0
    !text.isBlank()

    when:
    def (exitStatus, stdout, stderr) = writeAndRunShellScript(text, """
echo "\$$varName"
""")

    then:
    exitStatus == 0
    stderr.isEmpty()
    out.lines().count { it.startsWith('export \'') } == 15
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
    def (res, text) = runMain(['/test/shell-injection/', '--format=dotenv'])

    then:
    res == 0
    !text.isBlank()

    when:
    def (exitStatus, stdout, stderr) = writeAndRunShellScript(text, """
echo "\$$varName"
""")

    then:
    exitStatus == 0
    stderr.isEmpty()
    out.lines().count { it.startsWith('export ') } == 0
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

  def runMain(Collection<String> args, Consumer<List<String>> customizer = null) {
    def cliArgs = cliArgs(args, customizer)
    def exitStatus = Main.runMain(cliArgs)
    def text = out.toString()

    [exitStatus, text]
  }

  def writeAndRunShellScript(String text, String customCmd) {
    def scriptStr = """#!/bin/sh

# evaluate output
$text

# just a small function, to make sure it never gets invoked
my_super_function() {
  echo "I'm a superrrrrrrrrr function AND I SHOULD NOT HAVE BEEN INVOKED"
  exit 254
}

# the rest is a custom command
$customCmd
"""

    def scriptPath = Files.createTempFile(".test-script-", ".sh")
    log.info("will run shell script: $scriptPath:\n$scriptStr")

    try {
      Files.write(scriptPath, scriptStr.getBytes(StandardCharsets.UTF_8))

      def res = new ProcessBuilder()
          .command(["sh", scriptPath.toString()])
          .start()
          .waitForResult(1, TimeUnit.SECONDS)

      log.info("process exited with exit status ${res.exitCode}:\n---stderr---\n${res.err}---stderr---\n---stdout---\n${res.out}---stdout---")

      return [res.exitCode, res.out, res.err]
    } finally {
      Files.deleteIfExists(scriptPath)
    }
  }

  def cliArgs(Collection<String> args, Consumer<List<String>> customizer = null) {
    def res = ['--endpoint=http://localhost:4566', '--region=us-east-1'] + args.toList()
    if (customizer != null) {
      customizer.accept(res)
    }

    res.toArray(new String[0])
  }

  static class MyPrintStream extends PrintStream {
    private static final baos = new ByteArrayOutputStream()

    MyPrintStream() {
      super(baos, true)
    }

    List<String> lines() {
      baos.flush()
      baos.toString().lines().toList()
    }

    MyPrintStream clear() {
      baos.reset()
      return this
    }

    @Override
    String toString() {
      return baos.toString()
    }
  }
}
