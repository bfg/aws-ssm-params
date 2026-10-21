package com.github.bfg.awsssmparams


import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException
import software.amazon.awssdk.services.ssm.model.ResourceTypeForTagging
import software.amazon.awssdk.services.ssm.model.Tag

class CopyCmdSpec extends CmdSpecBase {
  def root = '/unit-test/copy-command/' + UUID.randomUUID()
  def sourcePath = "$root/source"
  def destinationPath = "$root/destination"
  List<String> parameterNames = []

  String subCommand() {
    'cp'
  }

  def cleanup() {
    ssmFacade.removeParameters(parameterNames)
  }

  String addParameter(String suffix, String value = 'value') {
    String name = "$sourcePath/$suffix"
    parameterNames.add(name)
    ssmClient.putParameter { it.name(name).value(value).type('String') }
    log.info("ADDED PARAMETER: $name")
    name
  }

  String ssmPath(String suffix) {
    String name = "$destinationPath/$suffix"
    parameterNames.add(name)
    name
  }

  def 'previews a single copy and copies only with do-it'() {
    given:
    String source = addParameter('foo', 'source-value')
    String destination = ssmPath('foo')

    when:
    def (status, stdout, stderr) = runCmd([source, destinationPath.toString()])

    then:
    status == 0
    stdout == "[dry-run] cp: $source -> $destination\n"
    stderr.isEmpty()

    when:
    ssmClient.getParameter { it.name(destination) }

    then:
    thrown(ParameterNotFoundException)

    when:
    (status, stdout, stderr) = runCmd(['-y', source, destinationPath.toString()])

    then:
    status == 0
    stdout == "cp: $source -> $destination\n"
    stderr.isEmpty()
    ssmClient.getParameter { it.name(destination) }.parameter().value() == 'source-value'
    ssmClient.getParameter { it.name(source) }.parameter().value() == 'source-value'
  }

  def 'copies matching parameters and preserves subpaths only with recursive option'() {
    given:
    String first = addParameter('foo-one', 'one')
    String second = addParameter('foo-two', 'two')
    String nested = addParameter('nested/foo-three', 'three')
    addParameter('bar', 'other')
    String firstTarget = ssmPath('foo-one')
    String secondTarget = ssmPath('foo-two')
    String nestedTarget = ssmPath('nested/foo-three')
    String pattern = "$sourcePath/foo*"

    when:
    def (status, stdout, stderr) = runCmd([pattern.toString(), destinationPath.toString()])

    then:
    status == 0
    stdout == "[dry-run] cp: $first -> $firstTarget\n[dry-run] cp: $second -> $secondTarget\n"
    stderr.isEmpty()

    when:
    (status, stdout, stderr) = runCmd(['-y', '-r', pattern.toString(), destinationPath.toString()])

    then:
    status == 0
    stdout == "cp: $first -> $firstTarget\ncp: $second -> $secondTarget\ncp: $nested -> $nestedTarget\n"
    stderr.isEmpty()
    ssmClient.getParameter { it.name(firstTarget) }.parameter().value() == 'one'
    ssmClient.getParameter { it.name(secondTarget) }.parameter().value() == 'two'
    ssmClient.getParameter { it.name(nestedTarget) }.parameter().value() == 'three'
  }

  def 'does not overwrite an existing destination'() {
    given:
    String source = addParameter('foo', 'source-value')
    String destination = ssmPath('foo')
    ssmClient.putParameter { it.name(destination).value('existing-value').type('String') }

    when:
    def (status, stdout, stderr) = runCmd(['-y', source, destinationPath.toString()])

    then:
    status == 1
    stdout.isEmpty()
    stderr.contains('copy operation failed')
    ssmClient.getParameter { it.name(destination) }.parameter().value() == 'existing-value'
  }

  def 'copies source parameter attributes'() {
    given:
    String source = "$sourcePath/secret"
    String destination = ssmPath('secret')
    parameterNames.add(source)
    ssmClient.putParameter {
      it.name(source)
          .value('secret')
          .type('SecureString')
          .keyId('alias/aws/ssm')
          .description('a secret')
          .allowedPattern('^[a-z]+$')
          .tier('Advanced')
          .tags(Tag.builder().key('team').value('ops').build())
    }
    def sourceMetadata = ssmClient.describeParameters { it.parameterFilters(filter -> filter.key('Name').option('Equals').values(source)) }.parameters()[0]
    def sourceTags = ssmClient.listTagsForResource { it.resourceType(ResourceTypeForTagging.PARAMETER).resourceId(source) }.tagList()

    when:
    def (status, stdout, stderr) = runCmd(['--do-it', source, destinationPath.toString()])

    then:
    status == 0
    stdout == "cp: $source -> $destination\n"
    stderr.isEmpty()
    def copied = ssmClient.getParameter { it.name(destination).withDecryption(true) }.parameter()
    copied.value() == 'secret'
    copied.typeAsString() == 'SecureString'
    def destinationMetadata = ssmClient.describeParameters { it.parameterFilters(filter -> filter.key('Name').option('Equals').values(destination)) }.parameters()[0]
    destinationMetadata.description() == sourceMetadata.description()
    destinationMetadata.allowedPattern() == sourceMetadata.allowedPattern()
    destinationMetadata.keyId() == sourceMetadata.keyId()
    destinationMetadata.tierAsString() == sourceMetadata.tierAsString()
    ssmClient.listTagsForResource { it.resourceType(ResourceTypeForTagging.PARAMETER).resourceId(destination) }.tagList() ==
        sourceTags
  }
}
