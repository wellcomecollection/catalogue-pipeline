package weco.catalogue.tei.id_extractor

import com.github.tomakehurst.wiremock.client.WireMock._
import org.apache.commons.io.IOUtils
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import weco.catalogue.tei.id_extractor.fixtures.Wiremock
import weco.http.client.AkkaHttpClient

import java.net.URI
import java.nio.charset.StandardCharsets
import scala.xml.XML
import scala.xml.Utility.trim

class GitHubBlobContentReaderTest
    extends AnyFunSpec
    with Wiremock
    with ScalaFutures
    with Matchers
    with Akka
    with IntegrationPatience {
  it("reads a blob from GitHub") {
    withWiremock("localhost") { port =>
      withActorSystem { implicit ac =>
      implicit val ec = ac.dispatcher
        val uri = new URI(
          s"http://localhost:$port/git/blobs/2e6b5fa45462510d5549b6bcf2bbc8b53ae08aed")
        val gitHubBlobReader = new GitHubBlobContentReader(new AkkaHttpClient(),"fake_token")
        whenReady(gitHubBlobReader.getBlob(uri)) { result =>
          val str = IOUtils.resourceToString(
            "/WMS_Arabic_1.xml",
            StandardCharsets.UTF_8)
          trim(XML.loadString(result)) shouldBe trim(XML.loadString(str))
        }
      }
    }
  }
  it("handles error from github") {
    withWiremock("localhost") { port =>
      withActorSystem { implicit ac =>
      implicit val ec = ac.dispatcher
        val uri = new URI(s"http://localhost:$port/git/blobs/123456789qwertyu")
        val gitHubBlobReader = new GitHubBlobContentReader(new AkkaHttpClient(),"fake_token")
        stubFor(
          get("/git/blobs/123456789qwertyu")
            .willReturn(serverError()
              .withBody("<response>ERROR!</response>")))

        whenReady(gitHubBlobReader.getBlob(uri).failed) { result =>
          result shouldBe a[RuntimeException]
          result.getMessage should include("Server Error")
        }
      }
    }
  }
}
