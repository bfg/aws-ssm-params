package com.github.bfg.awsssmparams


import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class AddCmdSpec extends CmdSpecBase {
  String parameterPath = '/unit-test/add-command/' + UUID.randomUUID()
  List<String> parameterNames = []
  List<Path> temporaryFiles = []

  String subCommand() {
    'add'
  }

  def cleanup() {
    parameterNames.each { name ->
      try {
        ssmClient.deleteParameter { it.name(name) }
      } catch (ParameterNotFoundException ignored) {
      }
    }
    temporaryFiles.each { Files.deleteIfExists(it) }
  }

  Path writeDotEnv(String content) {
    Path file = Files.createTempFile('add-command-', '.env')
    temporaryFiles.add(file)
    Files.writeString(file, content)
    file
  }

  String ssmPath(String key) {
    String result = "$parameterPath/$key"
    parameterNames.add(result)
    result
  }

  def 'reads multiple dotenv files and creates String parameters'() {
    given:
    Path first = writeDotEnv("""# comment
ALPHA${separator}"hello world"
HASH  ${separator}   value#part
""")
    Path second = writeDotEnv('BETA=literal $HOME\nEQUALS=one=two\n')
    String alpha = ssmPath('ALPHA')
    String hash = ssmPath('HASH')
    String beta = ssmPath('BETA')
    String equals = ssmPath('EQUALS')

    when:
    def (status, stdout, stderr) = runCmd([parameterPath, first.toString(), second.toString()])

    then:
    status == 0
    stdout == "add: $alpha\nadd: $hash\nadd: $beta\nadd: $equals\n"
    stderr.isEmpty()
    ssmClient.getParameter { it.name(alpha) }.parameter().value() == 'hello world'
    ssmClient.getParameter { it.name(hash) }.parameter().value() == 'value#part'
    ssmClient.getParameter { it.name(beta) }.parameter().value() == 'literal $HOME'
    ssmClient.getParameter { it.name(equals) }.parameter().value() == 'one=two'
    ssmClient.getParameter { it.name(alpha) }.parameter().typeAsString() == 'String'

    where:
    separator << [ '=', ':']
  }

  def 'does not overwrite a parameter without force'() {
    given:
    def target = ssmPath('VALUE')
    ssmClient.putParameter { it.name(target).value('original').type('String') }
    Path file = writeDotEnv('VALUE=replacement\n')

    when:
    def (status, stdout, stderr) = runCmd([parameterPath, file.toString()])

    then:
    status == 1
    stdout.isEmpty()
    stderr.contains('Add operation failed')
    ssmClient.getParameter { it.name(target) }.parameter().value() == 'original'

    when:
    (status, stdout, stderr) = runCmd(['-f', parameterPath, file.toString()])

    then:
    status == 0
    stdout == "add: $target\n"
    stderr.isEmpty()
    ssmClient.getParameter { it.name(target) }.parameter().value() == 'replacement'
  }

  def '[#option] creates the parameter type: #expectedType'() {
    given:
    def target = ssmPath('VALUE')
    Path file = writeDotEnv('VALUE=one,two\n')

    when:
    def args = [parameterPath, file.toString()]
    if (!option.isBlank()) args.addFirst(option)
    def (status, stdout, stderr) = runCmd(args)

    then:
    status == 0
    stdout == "add: $target\n"
    stderr.isEmpty()
    def parameter = ssmClient.getParameter { it.name(target).withDecryption(true) }.parameter()
    parameter.typeAsString() == expectedType
    parameter.value() == 'one,two'

    where:
    option     | expectedType
    ''         | 'String'
    '--list'   | 'StringList'
    '-s'       | 'SecureString'
    '--secure' | 'SecureString'
  }

  def 'reads stdin when no files are given'() {
    given:
    def target = ssmPath('VALUE')
    def input = new ByteArrayInputStream('VALUE=from-stdin\n'.getBytes(StandardCharsets.UTF_8))
    CmdRunner.stdioStreams(input, stdout, stderr)

    when:
    int status = CmdRunner.runMain(cliArgs([parameterPath]))

    then:
    status == 0
    stdout.text() == "add: $target\n"
    stderr.text().isEmpty()
    ssmClient.getParameter { it.name(target) }.parameter().value() == 'from-stdin'
  }

  def 'rejects malformed dotenv before creating parameters'() {
    given:
    def target = ssmPath('VALUE')
    def file = writeDotEnv('\nnot an assignment\n')

    when:
    def (status, stdout, stderr) = runCmd([parameterPath, file.toString()])

    then:
    status == 1
    stdout.isEmpty()
    stderr.contains('FATAL: No parameters were read from input files')

    when:
    ssmClient.getParameter { it.name(target) }

    then:
    thrown(ParameterNotFoundException)
  }
}
