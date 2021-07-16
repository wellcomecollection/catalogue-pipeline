package weco.catalogue.tei.id_extractor

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.akka.fixtures.Akka
import weco.catalogue.tei.id_extractor.fixtures.{LocalResources, Wiremock, XmlAssertions}
import weco.http.client.AkkaHttpClient

import java.net.URI

class GitHubBlobContentReaderTest
    extends AnyFunSpec
    with Wiremock
    with ScalaFutures
    with Matchers
    with Akka
    with IntegrationPatience
    with LocalResources
    with XmlAssertions {

  it("reads a blob from GitHub") {
    withWiremock("localhost") { port =>
      withActorSystem { implicit ac =>
        val uri = new URI(
          s"http://localhost:$port/git/blobs/2e6b5fa45462510d5549b6bcf2bbc8b53ae08aed")
        val gitHubBlobReader =
          new GitHubBlobContentReader(new AkkaHttpClient(), "fake_token")

        whenReady(gitHubBlobReader.getBlob(uri)) {
          assertXmlStringsAreEqual(_, readResource("/WMS_Arabic_1.xml"))
        }
      }
    }
  }
  it("strips bom in tei files read from GitHub") {
    withWiremock("localhost") { port =>
      withActorSystem { implicit ac =>
        val uri = new URI(
          s"http://localhost:$port/git/blobs/ddffeb761e5158b41a3780cda22346978d2cd6bd")
        val gitHubBlobReader =
          new GitHubBlobContentReader(new AkkaHttpClient(), "fake_token")

        whenReady(gitHubBlobReader.getBlob(uri)) {
          assertXmlStringsAreEqual(_, readResource("/Javanese_11.xml"))
        }
      }
    }
  }
  it("handles error from github") {
    withWiremock("localhost") { port =>
      withActorSystem { implicit ac =>
        val uri = new URI(s"http://localhost:$port/git/blobs/123456789qwertyu")
        val gitHubBlobReader =
          new GitHubBlobContentReader(new AkkaHttpClient(), "fake_token")
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
