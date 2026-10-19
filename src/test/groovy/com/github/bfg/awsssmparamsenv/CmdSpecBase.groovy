package com.github.bfg.awsssmparamsenv

import groovy.util.logging.Slf4j
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@Slf4j
abstract class CmdSpecBase extends Specification {
  BufPrintStream stdout = BufPrintStream.create("stdout")
  BufPrintStream stderr = BufPrintStream.create("stderr")

  def setup() {
    CmdRunner.stdioStreams(stdout, stderr)
  }

  def cleanup() {
    stdout.clear()
    stderr.clear()
    CmdRunner.resetStdioStreams()
  }

  String subCommand() {
    ""
  }

  def runCmd(Collection<String> args, Consumer<List<String>> customizer = null) {
    def argsArray = cliArgs(args, customizer)

    // clear streams
    CmdRunner.stdioStreams(stdout.clear(), stderr.clear())


    log.info("running cmdrunner: ${argsArray.toList()}")
    def exitStatus = CmdRunner.runMain(argsArray)
    def stdoutStr = stdout.text()
    def stderrStr = stderr.text()

    [exitStatus, stdoutStr, stderrStr]
  }

  def cliArgs(Collection<String> args, Consumer<List<String>> customizer = null) {
    def defaultArgs = ['--endpoint=http://localhost:4566', '--region=us-east-1']

    def subCommand = subCommand()
    if (!subCommand.isBlank()) {
      //defaultArgs.addFirst(subCommand)
      defaultArgs.add(subCommand)
    }

    def res = defaultArgs + args.toList()
    if (customizer != null) {
      customizer.accept(res)
    }

    res.toArray(new String[0])
  }

  def shellSourceOutput(String text, String customCmd) {
    def scriptStr = """#!/bin/sh
# just a small function, to make sure it never gets invoked
my_super_function() {
  echo "I'm a superrrrrrrrrr function AND I SHOULD NOT HAVE BEEN INVOKED"
  exit 254
}

# evaluate output
$text

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

      log.info("process exited with exit status ${res.exitCode}:\n---stdout---\n${res.out}---stdout---\n---stderr---\n${res.err}---stderr---\n")

      return [res.exitCode, res.out, res.err]
    } finally {
      Files.deleteIfExists(scriptPath)
    }
  }
}
