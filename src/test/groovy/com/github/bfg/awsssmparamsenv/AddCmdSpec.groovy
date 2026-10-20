package com.github.bfg.awsssmparamsenv

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class AddCmdSpec extends CmdSpecBase {
  String parameterPath = '/test/add-command/' + UUID.randomUUID()
  List<String> parameterNames = []
  List<Path> temporaryFiles = []
  SsmClient client = SsmClient.builder()
      .endpointOverride(URI.create('http://localhost:4566'))
      .region(Region.US_EAST_1)
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create('test', 'test')))
      .build()

  String subCommand() {
    'add'
  }

  def cleanup() {
    parameterNames.each { name ->
      try {
        client.deleteParameter { it.name(name) }
      } catch (ParameterNotFoundException ignored) {
      }
    }
    client.close()
    temporaryFiles.each { Files.deleteIfExists(it) }
  }

  Path dotenv(String content) {
    Path file = Files.createTempFile('add-command-', '.env')
    temporaryFiles.add(file)
    Files.writeString(file, content)
    file
  }

  String name(String key) {
    String result = "$parameterPath/$key"
    parameterNames.add(result)
    result
  }

  def 'reads multiple dotenv files and creates String parameters'() {
    given:
    Path first = dotenv('''# comment
export ALPHA="hello\\nworld"
HASH=value#part
''')
    Path second = dotenv("BETA='literal \$HOME'\n")
    String alpha = name('ALPHA')
    String hash = name('HASH')
    String beta = name('BETA')

    when:
    def (status, stdout, stderr) = runCmd([parameterPath, first.toString(), second.toString()])

    then:
    status == 0
    stdout == "add: $alpha\nadd: $hash\nadd: $beta\n"
    stderr.isEmpty()
    client.getParameter { it.name(alpha) }.parameter().value() == 'hello\nworld'
    client.getParameter { it.name(hash) }.parameter().value() == 'value#part'
    client.getParameter { it.name(beta) }.parameter().value() == 'literal $HOME'
    client.getParameter { it.name(alpha) }.parameter().typeAsString() == 'String'
  }

  def 'does not overwrite a parameter without force'() {
    given:
    String target = name('VALUE')
    client.putParameter { it.name(target).value('original').type('String') }
    Path file = dotenv('VALUE=replacement\n')

    when:
    def (status, stdout, stderr) = runCmd([parameterPath, file.toString()])

    then:
    status == 1
    stdout.isEmpty()
    stderr.contains('Add operation failed')
    client.getParameter { it.name(target) }.parameter().value() == 'original'

    when:
    (status, stdout, stderr) = runCmd(['-f', parameterPath, file.toString()])

    then:
    status == 0
    stdout == "add: $target\n"
    stderr.isEmpty()
    client.getParameter { it.name(target) }.parameter().value() == 'replacement'
  }

  def '[#option] creates the requested parameter type'() {
    given:
    String target = name('VALUE')
    Path file = dotenv('VALUE=one,two\n')

    when:
    def (status, stdout, stderr) = runCmd([option, parameterPath, file.toString()])

    then:
    status == 0
    stdout == "add: $target\n"
    stderr.isEmpty()
    def parameter = client.getParameter { it.name(target).withDecryption(true) }.parameter()
    parameter.typeAsString() == expectedType
    parameter.value() == 'one,two'

    where:
    option     | expectedType
    '-l'       | 'StringList'
    '--list'   | 'StringList'
    '-s'       | 'SecureString'
    '--secure' | 'SecureString'
  }

  def 'reads stdin when no files are given'() {
    given:
    String target = name('VALUE')
    def input = new ByteArrayInputStream('VALUE=from-stdin\n'.getBytes(StandardCharsets.UTF_8))
    CmdRunner.stdioStreams(input, stdout, stderr)

    when:
    int status = CmdRunner.runMain(cliArgs([parameterPath]))

    then:
    status == 0
    stdout.text() == "add: $target\n"
    stderr.text().isEmpty()
    client.getParameter { it.name(target) }.parameter().value() == 'from-stdin'
  }

  def 'rejects malformed dotenv before creating parameters'() {
    given:
    String target = name('VALUE')
    Path file = dotenv('VALUE=valid\nnot an assignment\n')

    when:
    def (status, stdout, stderr) = runCmd([parameterPath, file.toString()])

    then:
    status == 1
    stdout.isEmpty()
    stderr.contains('expected KEY=VALUE')

    when:
    client.getParameter { it.name(target) }

    then:
    thrown(ParameterNotFoundException)
  }
}
