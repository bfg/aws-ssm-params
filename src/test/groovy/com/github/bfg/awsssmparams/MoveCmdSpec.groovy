package com.github.bfg.awsssmparams

import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException

class MoveCmdSpec extends CmdSpecBase {
  String root = '/unit-test/move-command/' + UUID.randomUUID()
  String sourcePath = "$root/source"
  String destinationPath = "$root/destination"
  List<String> parameterNames = []

  String subCommand() {
    'mv'
  }

  def cleanup() {
    ssmFacade.removeParameters(parameterNames)
  }

  String addParameter(String suffix, String value = 'value') {
    String name = "$sourcePath/$suffix"
    parameterNames.add(name)
    ssmClient.putParameter { it.name(name).value(value).type('String') }
    name
  }

  String destination(String suffix) {
    String name = "$destinationPath/$suffix"
    parameterNames.add(name)
    name
  }

  boolean exists(String name) {
    try {
      ssmClient.getParameter { it.name(name) }
      return true
    } catch (ParameterNotFoundException ignored) {
      return false
    }
  }

  def 'previews a single move and removes the source only with do-it'() {
    given:
    String source = addParameter('foo', 'source-value')
    String target = destination('foo')

    when:
    def (status, stdout, stderr) = runCmd([source, destinationPath])

    then:
    status == 0
    stdout == "[dry-run] mv: $source -> $target\n"
    stderr.isEmpty()
    exists(source)
    !exists(target)

    when:
    (status, stdout, stderr) = runCmd(['-y', source, destinationPath])

    then:
    status == 0
    stdout == "mv: $source -> $target\n"
    stderr.isEmpty()
    !exists(source)
    ssmClient.getParameter { it.name(target) }.parameter().value() == 'source-value'
  }

  def 'moves matching parameters in subpaths with recursive option'() {
    given:
    String first = addParameter('foo-one', 'one')
    String nested = addParameter('nested/foo-two', 'two')
    String untouched = addParameter('nested/bar', 'other')
    String firstTarget = destination('foo-one')
    String nestedTarget = destination('nested/foo-two')

    when:
    def (status, stdout, stderr) = runCmd(['-y', '--recursive', "$sourcePath/foo*".toString(), destinationPath])

    then:
    status == 0
    stdout == "mv: $first -> $firstTarget\nmv: $nested -> $nestedTarget\n"
    stderr.isEmpty()
    !exists(first)
    !exists(nested)
    exists(untouched)
    ssmClient.getParameter { it.name(firstTarget) }.parameter().value() == 'one'
    ssmClient.getParameter { it.name(nestedTarget) }.parameter().value() == 'two'
  }

  def 'failed copy leaves the source in place and force permits the move'() {
    given:
    String source = addParameter('foo', 'new-value')
    String target = destination('foo')
    ssmClient.putParameter { it.name(target).value('old-value').type('String') }

    when:
    def (status, stdout, stderr) = runCmd(['-y', source, destinationPath])

    then:
    status == 1
    stdout.isEmpty()
    stderr.contains('move operation failed')
    exists(source)
    ssmClient.getParameter { it.name(target) }.parameter().value() == 'old-value'

    when:
    (status, stdout, stderr) = runCmd(['-y', '-f', source, destinationPath])

    then:
    status == 0
    stdout == "mv: $source -> $target\n"
    stderr.isEmpty()
    !exists(source)
    ssmClient.getParameter { it.name(target) }.parameter().value() == 'new-value'
  }
}
