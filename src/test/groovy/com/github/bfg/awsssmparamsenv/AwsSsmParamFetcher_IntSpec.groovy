package com.github.bfg.awsssmparamsenv


import software.amazon.awssdk.services.ssm.SsmClient
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

@Ignore
class AwsSsmParamFetcher_IntSpec extends Specification {
  @Shared
  @AutoCleanup
  def fetcher = createFetcher()

  def createFetcher() {
    SsmClient client = SsmClient.builder().build()
    new AwsSsmParamFetcher(client)
  }

  SsmParamQuery.SsmParamQueryBuilder newBuilder() {
    SsmParamQuery.builder()
  }


  def '[default, top, no-recurse] should return expected result'() {
    given:
    def query = newBuilder().path('/copilot/applications').build()

    when:
    def res = fetcher.fetchParameters(query)
    def multiLineStr = res.multiline_string
    def multilineJson = res.multiline_json

    then:
    multiLineStr == "multi \n" + "  line "
    multilineJson == '''{
  "name": "trololo-app",
  "accountNum": 909081655695,
  "domains": [
    "foo",
    "bar"
  ],
  "domainHostedZoneID": "multi\\n ku'lti",
  "version": "1.0"
}'''
  }
}
