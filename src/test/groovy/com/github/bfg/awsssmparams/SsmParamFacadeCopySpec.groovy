package com.github.bfg.awsssmparams


import software.amazon.awssdk.services.ssm.model.ParameterType
import software.amazon.awssdk.services.ssm.model.Tag
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Stepwise

@Stepwise
class SsmParamFacadeCopySpec extends CmdSpecBase {
  @Shared
  def rootPath = "/unit-test/cp/${UUID.randomUUID()}"

  @Shared
  def paramValue = UUID.randomUUID().toString()

  @Shared
  def paramDesc = "some-param-desc"

  def source = "$rootPath/source/foo"
  def destination = "$rootPath/destination/bar"

  @Shared
  @AutoCleanup
  def facade = new SsmParamFacade(createSsmClient())

  @Shared
  def copiedParamPath

  def "create a source parameter"() {
    given:
    def tag = Tag.builder().key('team').value('ops').build()
    def firstPolicy = '{"Type":"NoChangeNotification","Version":"1.0","Attributes":{"After":"30","Unit":"Days"}}'
    def secondPolicy = '{"Type":"ExpirationNotification","Version":"1.0","Attributes":{"Before":"1","Unit":"Days"}}'

    when:
    def res = ssmClient.putParameter {
      it.name(source)
          .value(paramValue)
          .type(ParameterType.SECURE_STRING)
          .tags(tag)
          .description(paramDesc)
          .allowedPattern('^[a-z-]+$')
          .tier('Advanced')
    }

    then:
    noExceptionThrown()
    res != null
  }

  def "should NOT copy a parameter because do it is not given"() {
    given:
    def copyCfg = SsmCopyParamConfig.builder()
        .source(source)
        .destination(destination)
        .build()

    def calledWith = []

    when:
    def res = facade.copyParameters(copyCfg) { calledWith.add(it) }

    then:
    res == 0
    calledWith.size() == 1 // should be invoked regardless

    facade.exists(source)
    !facade.exists(destination)
  }

  def "should copy a parameter"() {
    given:
    def copyCfg = SsmCopyParamConfig.builder()
        .source(source)
        .destination(destination)
        .doIt(true)
        .build()

    def calledWith = []
    copiedParamPath = destination + '/' + Utils.basename(source)

    when:
    def res = facade.copyParameters(copyCfg) { calledWith.add(it) }

    then:
    res == 1
    calledWith.size() == 1

    facade.exists(source)
    facade.exists(copiedParamPath)
  }

  def "copy should contain expected stuff"() {
    when:
    def copy = facade.fetchParameter(copiedParamPath)

    then:
    with(copy) {
      it.name() == copiedParamPath
      it.typeAsString() == 'SecureString'
      it.value() == paramValue
    }
  }

  def "should remove created parameters"() {
    given:
    def cfg = SsmRemoveParamConfig.builder()
        .path(rootPath)
        .recursive(true)
        .doIt(true)
        .build()

    expect:
    facade.removeParameters(cfg) >= 2
  }
}
