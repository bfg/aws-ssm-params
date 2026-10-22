package com.github.bfg.awsssmparams


import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssm.SsmClient
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

abstract class CmdSpecBase extends Specification {
  static def awsEndpoint = 'http://localhost:4566'
  static def awsRegion = 'us-east-1'

  Logger log = LoggerFactory.getLogger(getClass())
  BufPrintStream stdout = BufPrintStream.create("stdout")
  BufPrintStream stderr = BufPrintStream.create("stderr")

  @AutoCleanup
  def ssmClient = createSsmClient()

  @AutoCleanup
  def ssmFacade = new SsmParamFacade(ssmClient)

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

  SsmClient createSsmClient() {
    SsmClient.builder()
        .endpointOverride(URI.create(awsEndpoint))
        .region(Region.of(awsRegion))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create('test', 'test')))
        .build()
  }

  def runCmd(Collection<String> args, Consumer<List<String>> customizer = null) {
    def argsArray = cliArgs(args, customizer)

    // clear streams
    CmdRunner.stdioStreams(stdout.clear(), stderr.clear())

    log.info("running cmdrunner: ${argsArray.toList()}")
    def status = CmdRunner.runMain(argsArray)
    def stdoutStr = stdout.text()
    def stderrStr = stderr.text()

    log.info("command done, exit status: $status\n---stdout---\n$stdoutStr---stdout---\n---stderr---\n$stderrStr---stderr---\n")

    [status, stdoutStr, stderrStr]
  }

  def cliArgs(Collection<String> args, Consumer<List<String>> customizer = null) {
    def defaultArgs = ['--endpoint=' + awsEndpoint, '--region=' + awsRegion]

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
