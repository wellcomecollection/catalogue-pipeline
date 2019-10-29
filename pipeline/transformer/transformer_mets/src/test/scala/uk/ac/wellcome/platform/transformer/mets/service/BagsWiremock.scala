package uk.ac.wellcome.platform.transformer.mets.service

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.scalatest.Suite

import scala.util.Try

trait BagsWiremock { this: Suite =>

  def withBagsService[R](port: Int, host: String)(testWith: => R): R = {
    val wireMockServer = new WireMockServer(
      WireMockConfiguration
        .wireMockConfig()
        .usingFilesUnderClasspath(".")
        .port(port))
    wireMockServer.start()
    WireMock.configureFor(host, port)
    val result = Try(testWith)
    wireMockServer.stop()
    result.get
  }
}
