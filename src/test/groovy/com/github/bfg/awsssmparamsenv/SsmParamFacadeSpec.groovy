package com.github.bfg.awsssmparamsenv


import com.google.gson.GsonBuilder
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssm.SsmClient
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SsmParamFacadeSpec extends Specification {
  private static final List<String> PATHS = ['/test/alpha', '/test/beta', '/test/gamma']

  @Shared
  @AutoCleanup
  def facade = createFacade()

  def gson = new GsonBuilder()
      .disableHtmlEscaping()
      .setPrettyPrinting().create()

  SsmParamQuery.SsmParamQueryBuilder newBuilder() {
    SsmParamQuery.builder().paths(PATHS)
  }

  SsmParamFacade createFacade() {
    SsmClient client = SsmClient.builder()
        .endpointOverride(URI.create('http://localhost:4566'))
        .region(Region.US_EAST_1)
        .build()

    new SsmParamFacade(client)
  }

  def 'fetchParametersAsMap[default, top, no-recurse] should return expected result'() {
    given:
    def query = newBuilder().clearPaths().path('/test').build()

    when:
    def res = facade.fetchParameterValuesAsMap(query)

    then:
    res.isEmpty()
  }

  def 'fetchParametersAsMap[default] fetchParameters should return expected result'() {
    given:
    def query = newBuilder().build()

    when:
    def res = facade.fetchParameterValuesAsMap(query)

    then:
    assertJson(res, [
        duplicate   : 'alpha-duplicate',
        display_name: ' Alpha Display Name ',
        token       : 'alpha-token',
        endpoint    : 'beta-endpoint',
        password    : 'beta-password',
        enabled     : 'gamma-enabled',
        api_key     : 'gamma-api_key',
        foo_bar_BaZ : 'gamma-foo-bar-baz',
    ])
  }

  def 'fetchParametersAsMap[trim,last_one] should return expected result'() {
    given:
    def query = newBuilder()
        .useLast(true)
        .trimValues(true)
        .build()

    when:
    def res = facade.fetchParameterValuesAsMap(query)

    then:
    assertJson(res, [
        duplicate   : 'beta-duplicate',
        display_name: 'Alpha Display Name',
        token       : 'alpha-token',
        endpoint    : 'beta-endpoint',
        password    : 'beta-password',
        enabled     : 'gamma-enabled',
        api_key     : 'gamma-api_key',
        foo_bar_BaZ : 'gamma-foo-bar-baz',

    ])
  }

  def 'fetchParametersAsMap[trim,last_one, sort] should return expected result'() {
    given:
    def query = newBuilder()
        .useLast(true)
        .trimValues(true)
        .sort(true)
        .build()

    when:
    def res = facade.fetchParameterValuesAsMap(query)

    then:
    assertJson(res, [
        api_key     : 'gamma-api_key',
        display_name: 'Alpha Display Name',
        duplicate   : 'beta-duplicate',
        enabled     : 'gamma-enabled',
        endpoint    : 'beta-endpoint',
        foo_bar_BaZ : 'gamma-foo-bar-baz',
        password    : 'beta-password',
        token       : 'alpha-token',
    ])
  }

  def 'fetchParametersAsMap[trim,last_one,reverse-sort] should return expected result'() {
    given:
    def query = newBuilder()
        .useLast(true)
        .trimValues(true)
        .reverseSort(true)
        .build()

    when:
    def res = facade.fetchParameterValuesAsMap(query)

    then:
    assertJson(res, [
        token       : 'alpha-token',
        password    : 'beta-password',
        foo_bar_BaZ : 'gamma-foo-bar-baz',
        endpoint    : 'beta-endpoint',
        enabled     : 'gamma-enabled',
        duplicate   : 'beta-duplicate',
        display_name: 'Alpha Display Name',
        api_key     : 'gamma-api_key',
    ])
  }

  def 'fetchParametersAsMap[trim,last_one,reverse-sort] should return expected result'() {
    given:
    def query = newBuilder()
        .useLast(true)
        .trimValues(true)
        .reverseSort(true)
        .uppercase(true)
        .build()

    when:
    def res = facade.fetchParameterValuesAsMap(query)

    then:
    assertJson(res, [
        TOKEN       : 'alpha-token',
        PASSWORD    : 'beta-password',
        FOO_BAR_BAZ : 'gamma-foo-bar-baz',
        ENDPOINT    : 'beta-endpoint',
        ENABLED     : 'gamma-enabled',
        DUPLICATE   : 'beta-duplicate',
        DISPLAY_NAME: 'Alpha Display Name',
        API_KEY     : 'gamma-api_key',
    ])
  }

  def 'fetchParametersAsMap[gamma] should return expected result'() {
    given:
    def query = newBuilder()
        .clearPaths()
        .path('/test/gamma')
        .trimValues(true)
        .sort(true)
        .uppercase(true)
        .build()

    when:
    def res = facade.fetchParameterValuesAsMap(query)

    then:
    assertJson(res, [
        API_KEY    : 'gamma-api_key',
        ENABLED    : 'gamma-enabled',
        FOO_BAR_BAZ: 'gamma-foo-bar-baz',
    ])
  }

  def 'fetchParametersAsMap[shell-injection] should return expected result'() {
    given:
    def query = newBuilder()
        .clearPaths()
        .path('/test/shell-injection')
        .recursive(true)
        .trimValues(true)
        .sort(true)
        .uppercase(true)
        .build()

    when:
    def res = facade.fetchParameterValuesAsMap(query)

    then:
    assertJson(res, [
        A: 'a=`ls /etc`',
        B: 'b=$(ls /etc)',
        C: '"x',
        D: 'x"',
        E: '"x"',
        F: "'x\"",
        G: '"\'x',
        H: '$HOME',
        I: 'eval x=$((1 + 1))',
        J: "{\"app\":\"trololo-app\",\"name\":\"trololo-app\",\"type\":\"Load Balanced Web Service \"}",
        K: "foo\\n  bar",
        L: "foo\\n\\t \\n \\t bar",
        M: '`ls /etc`',
        N: '$(ls /etc)',
        O: 'my_super_function'
    ])
  }

  def 'fetchParametersAsMap[all should be fetched] should return expected result'() {
    given:
    def query = newBuilder()
        .clearPaths()
        .path('/test////')
        .recursive(true)
        .trimValues(true)
        .sort(true)
        .uppercase(true)
        .build()

    when:
    def res = facade.fetchParameterValuesAsMap(query)

    then:
    assertJson(res, [
        "ALPHA_DISPLAY_NAME": "Alpha Display Name",
        "ALPHA_DUPLICATE"   : "alpha-duplicate",
        "ALPHA_TOKEN"       : "alpha-token",
        "BETA_DUPLICATE"    : "beta-duplicate",
        "BETA_ENDPOINT"     : "beta-endpoint",
        "BETA_PASSWORD"     : "beta-password",
        "GAMMA_0BAZ"        : "gamma-zero-baz",
        "GAMMA_API_KEY"     : "gamma-api_key",
        "GAMMA_ENABLED"     : "gamma-enabled",
        "GAMMA_FOO_BAR_BAZ" : "gamma-foo-bar-baz",
        "SHELL_INJECTION_A" : "a=`ls /etc`",
        "SHELL_INJECTION_B" : "b=\$(ls /etc)",
        "SHELL_INJECTION_C" : "\"x",
        "SHELL_INJECTION_D" : "x\"",
        "SHELL_INJECTION_E" : "\"x\"",
        "SHELL_INJECTION_F" : "'x\"",
        "SHELL_INJECTION_G" : "\"'x",
        "SHELL_INJECTION_H" : "\$HOME",
        "SHELL_INJECTION_I" : "eval x=\$((1 + 1))",
        "SHELL_INJECTION_J" : '{"app":"trololo-app","name":"trololo-app","type":"Load Balanced Web Service "}',
        "SHELL_INJECTION_K" : "foo\\n  bar",
        "SHELL_INJECTION_L" : "foo\\n\\t \\n \\t bar",
        "SHELL_INJECTION_M" : "`ls /etc`",
        "SHELL_INJECTION_N" : "\$(ls /etc)",
        "SHELL_INJECTION_O" : "my_super_function"])
  }

  def assertJson(def actual, def expected) {
    def actualJson = gson.toJson(actual)
    def expectedJson = gson.toJson(expected)

    assert actualJson == expectedJson
    true
  }
}
