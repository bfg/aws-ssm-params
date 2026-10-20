package com.github.bfg.awsssmparams


import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException

class RemoveCmdSpec extends CmdSpecBase {
  String parameterPath = '/test/remove-command/' + UUID.randomUUID()
  List<String> parameterNames = []

  String subCommand() {
    'rm'
  }

  def cleanup() {
    parameterNames.each { name ->
      try {
        ssmClient.deleteParameter { it.name(name) }
      } catch (ParameterNotFoundException ignored) {
      }
    }
  }

  String createSsmParameter(String name) {
    String path = "$parameterPath/$name"
    parameterNames.add(path)
    ssmClient.putParameter { it.name(path).value('value').type('String') }
    path
  }

  boolean parameterExists(String path) {
    try {
      ssmClient.getParameter { it.name(path) }
      return true
    } catch (ParameterNotFoundException ignored) {
      return false
    }
  }

  def "should NOT remove one named parameter because -y/--do-it wasn't given"() {
    given:
    String first = createSsmParameter('one')
    String second = createSsmParameter('nested/two')

    when:
    def (status, stdout, stderr) = runCmd([first])

    then:
    status == 0
    stdout == "[dry-run] rm: $first\n"
    stderr.isEmpty()
    parameterExists(first)
    parameterExists(second)
  }

  def 'should remove one named parameter if #doIt is given'() {
    given:
    String target = createSsmParameter('one')
    String other = createSsmParameter('nested/two')

    when:
    def (status, stdout, stderr) = runCmd([doIt, target])

    then:
    status == 0
    stdout == "rm: $target\n"
    stderr.isEmpty()
    !parameterExists(target)
    parameterExists(other)

    where:
    doIt << [ '-y', '--do-it' ]
  }

  def 'glob removes matching direct children only'() {
    given:
    String first = createSsmParameter('one')
    String second = createSsmParameter('two')
    String nested = createSsmParameter('nested/three')

    when:
    def (status, stdout, stderr) = runCmd(['-y', "$parameterPath/*".toString()])

    then:
    status == 0
    stdout == "rm: $first\nrm: $second\n"
    stderr.isEmpty()
    !parameterExists(first)
    !parameterExists(second)
    parameterExists(nested)
  }

  def 'removes multiple paths without deleting an overlapping match twice'() {
    given:
    String first = createSsmParameter('one')
    String second = createSsmParameter('two')
    String nested = createSsmParameter('nested/three')

    when:
    def (status, stdout, stderr) = runCmd(['-y', first, "$parameterPath/t*".toString(), "$parameterPath/o*".toString()])

    then:
    status == 0
    stdout.contains("rm: $first\n")
    stdout.contains("rm: $second\n")
    stderr.isEmpty()

    !parameterExists(first)
    !parameterExists(second)
    parameterExists(nested)
  }

  def '[#option] glob removes matching parameters in subpaths'() {
    given:
    String first = createSsmParameter('one')
    String nested = createSsmParameter('nested/two')

    when:
    def (status, stdout, stderr) = runCmd(['-y', option, "$parameterPath/*".toString()])

    then:
    status == 0
    stdout.lines().toList().toSet() == ["rm: $first", "rm: $nested"].toSet()
    stderr.isEmpty()
    !parameterExists(first)
    !parameterExists(nested)

    where:
    option << ['-r', '--recursive']
  }

  def 'should remove a single parameter even with recursion'() {
    given:
    String first = createSsmParameter('one')
    String second = createSsmParameter('nested/two')

    when:
    def (status, stdout, stderr) = runCmd(['-yr', first])

    then:
    status == 0
    stdout.contains("rm: " + first)
    !stdout.contains("rm: " + second)
    stderr.isEmpty()

    !parameterExists(first)
    parameterExists(second)
  }
}
