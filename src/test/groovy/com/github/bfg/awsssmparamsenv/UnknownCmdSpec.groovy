package com.github.bfg.awsssmparamsenv


import groovy.util.logging.Slf4j

@Slf4j
class UnknownCmdSpec extends CmdSpecBase {
  String subCommand() {
    "trololo"
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

  def "should exit immediately with exit status 1"() {
    when:
    def (status, stdout, stderr) = runCmd(args)

    then:
    status >= 1
    stdout.isBlank()
    !stderr.isEmpty()

    where:
    args << [
        ['--'],
        ['foo', 'bar'],
    ]
  }
}
