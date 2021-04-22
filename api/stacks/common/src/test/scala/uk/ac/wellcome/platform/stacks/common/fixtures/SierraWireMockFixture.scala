package uk.ac.wellcome.platform.stacks.common.fixtures

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import uk.ac.wellcome.fixtures.TestWith

trait SierraWireMockFixture {
  def withMockSierraServer[R](
    testWith: TestWith[(String, WireMockServer), R]
  ): R = {

    val wireMockServer = new WireMockServer(
      WireMockConfiguration
        .wireMockConfig()
        .usingFilesUnderClasspath(
          "./api/stacks/common/src/test/resources/sierra")
        .dynamicPort()
    )

    wireMockServer.start()

    val urlAndServer =
      (s"http://localhost:${wireMockServer.port()}", wireMockServer)
    val result = testWith(urlAndServer)

    wireMockServer.shutdown()

    result
  }
}
