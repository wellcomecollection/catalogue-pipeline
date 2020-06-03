package uk.ac.wellcome.mets_adapter.fixtures

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.scalatest.Suite
import uk.ac.wellcome.fixtures.TestWith

import scala.util.Try

trait BagsWiremock { this: Suite =>
  def withBagsService[R](host: String)(testWith: TestWith[Int, R]): R = {
    val wireMockServer = new WireMockServer(
      WireMockConfiguration
        .wireMockConfig()
        .usingFilesUnderClasspath(".")
        .dynamicPort())
    wireMockServer.start()
    val port = wireMockServer.port()
    WireMock.configureFor(host, port)
    val result = Try(testWith(port))
    wireMockServer.stop()
    result.get
  }
}
