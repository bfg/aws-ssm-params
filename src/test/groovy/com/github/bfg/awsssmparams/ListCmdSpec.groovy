package com.github.bfg.awsssmparams

import groovy.util.logging.Slf4j

import java.time.Instant

@Slf4j
class ListCmdSpec extends CmdSpecBase {
  String subCommand() {
    "ls"
  }

  def 'list should produce expected resilt'() {
    when:
    def (status, stdout, stderr) = runCmd(args)
    def params = toParamNames(stdout)

    then:
    status == 0
    stderr.isEmpty()
    params.toSet() == expected.toSet()

    where:
    args            | expected
    ['/',] | []
    ['/', '/test'] | []
    [ '/test/'] | []
    [ '/test///'] | []
    [ '////test///'] | []
    ['/test/alpha'] | [
        '/test/alpha/duplicate',
        '/test/alpha/display_name',
        '/test/alpha/token']
    ['/test/alpha', '/non-existent'] | [
        '/test/alpha/duplicate',
        '/test/alpha/display_name',
        '/test/alpha/token']
  }


  def '[#recursive] lists parameters in subpaths'() {
    when:
    def (status, stdout, stderr) = runCmd([recursive, '/test'])
    def params = toParamNames(stdout)

    then:
    status == 0
    stderr.isEmpty()
    params.size() >= 25

    where:
    recursive << ['-r', '--recursive']
  }

  def 'lists only parameters matching a basename glob'() {
    when:
    def (status, stdout, stderr) = runCmd(['/test/alpha/*name'])
    def params = toParamNames(stdout)

    then:
    status == 0
    stderr.isEmpty()
    params == ['/test/alpha/display_name']
  }

  def 'lists multiple paths with aligned parameter types'() {
    when:
    def (status, stdout, stderr) = runCmd(['/test/alpha/*name', '/test/beta/*word'])
    def params = toParamNames(stdout)

    then:
    status == 0
    stderr.isEmpty()
    params.toSet() == [
        '/test/alpha/display_name',
        '/test/beta/password'
    ].toSet()
  }

  def 'should not not list subpaths without recursive option'() {
    when:
    def (status, stdout, stderr) = runCmd(['/test'])
    def params = toParamNames(stdout)

    then:
    status == 0
    stderr.isEmpty()
    params.isEmpty()
  }

  List<List<String>> toParams(String s) {
    s.lines()
        .toList()
        .collect {
          def (type, modifiedAt, name) = it.split("\\s+", 3).toList()
          [name, Instant.parse(modifiedAt), type]
        }
  }

  List<String> toParamNames(String s) {
    toParams(s).collect { it[0] }
  }
}
