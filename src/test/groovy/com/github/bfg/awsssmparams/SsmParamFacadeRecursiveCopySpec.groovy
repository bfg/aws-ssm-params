package com.github.bfg.awsssmparams

import software.amazon.awssdk.services.ssm.model.Parameter
import software.amazon.awssdk.services.ssm.model.ParameterType
import spock.lang.Shared
import spock.lang.Stepwise

@Stepwise
class SsmParamFacadeRecursiveCopySpec extends CmdSpecBase {
  @Shared
  def rootPath = "/unit-test/cp-rec/${UUID.randomUUID()}"

  @Shared
  def paramNames = ["a", "b", "c/x/y/z", "c/x/y/w"]

  @Shared
  List<Parameter> sourceParameters = []

  def srcDir = "$rootPath/source"
  def dstDir = "$rootPath/destination"

  def "create a source parameters"() {
    when:
    def parameters = paramNames.collect {
      def path = "$srcDir/x/$it"
      ssmFacade.addParameter(path, UUID.randomUUID().toString(), ParameterType.STRING, true)
    }

    then:
    parameters.size() == 4

    cleanup:
    this.sourceParameters = parameters
  }

  def "should NOT copy discovered parameters [do-it omitted]"() {
    given:
    def cfg = SsmCopyParamConfig.builder()
        .source(srcDir)
        .destination(dstDir)
        .recursive(true)
        .build()

    when:
    //List<Map.Entry<String, String>> copied = []
    def copied = []
    def res = ssmFacade.copyParameters(cfg) { copied.add(it) }

    then:
    res == 0
    copied.size() == 4

    // parameters should be discovered
    paramNames.each {
      def dstPath = "$dstDir/x/$it"
      assert copied.find { e -> e.value == dstPath } != null
    }
  }

  def "should NOT copy discovered parameters [recursive omitted]"() {
    given:
    def cfg = SsmCopyParamConfig.builder()
        .source(srcDir)
        .destination(dstDir)
        .doIt(true)
        .build()

    when:
    def copied = []
    def res = ssmFacade.copyParameters(cfg) { copied.add(it) }

    then:
    res == 0
    copied.isEmpty()
  }

  def "should copy discovered parameters"() {
    given:
    def cfg = SsmCopyParamConfig.builder()
        .source(srcDir)
        .destination(dstDir)
        .recursive(true)
        .doIt(true)
        .build()

    when:
    def copied = []
    def res = ssmFacade.copyParameters(cfg) { copied.add(it) }

    then:
    res == 4

    // parameters should be discovered
    paramNames.each {
      def dstPath = "$dstDir/x/$it"
      assert copied.find { e -> e.value == dstPath } != null
    }

    // new parameters should exist
    paramNames.each {
      def dstPath = "$dstDir/x/$it"
      assert ssmFacade.exists(dstPath)

    }
  }

  def "should remove created parameters"() {
    when:
    def cfg = SsmRemoveParamConfig.builder()
        .path(rootPath)
        .recursive(true)
        .doIt(true)
        .build()

    ssmFacade.removeParameters(cfg)

    then:
    true
  }
}
