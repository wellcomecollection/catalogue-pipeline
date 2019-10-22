package uk.ac.wellcome.platform.transformer.mets.service

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.scalatest.{Assertion, BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import uk.ac.wellcome.fixtures.TestWith

trait BagsWiremock { this: Suite =>

  def withBagsService[R](port: Int, host: String)(testWith: TestWith[Unit,R]): R = {
    val wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().usingFilesUnderClasspath(".").port(port))
    wireMockServer.start()
    WireMock.configureFor(host, port)
    val result = testWith(())
    wireMockServer.stop()
    result
  }
}
