package weco.catalogue.tei.id_extractor

import akka.http.scaladsl.model._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.akka.fixtures.Akka
import weco.catalogue.tei.id_extractor.fixtures.XmlAssertions
import weco.fixtures.LocalResources
import weco.http.client.MemoryHttpClient

import java.net.URI
import scala.concurrent.ExecutionContext.Implicits.global

class GitHubBlobContentReaderTest
    extends AnyFunSpec
    with ScalaFutures
    with Matchers
    with Akka
    with IntegrationPatience
    with LocalResources
    with XmlAssertions {

  it("reads a blob from GitHub") {
    val uri =
      "http://github:1234/git/blobs/2e6b5fa45462510d5549b6bcf2bbc8b53ae08aed"

    val responses = Seq(
      (
        HttpRequest(uri = Uri(uri)),
        HttpResponse(
          entity = HttpEntity(
            contentType = ContentTypes.`application/json`,
            readResource("github-blob-2e6b5fa.json")
          )
        )
      )
    )

    val httpClient = new MemoryHttpClient(responses)

    withActorSystem { implicit ac =>
      val gitHubBlobReader = new GitHubBlobContentReader(httpClient)

      whenReady(gitHubBlobReader.getBlob(new URI(uri))) {
        assertXmlStringsAreEqual(_, readResource("WMS_Arabic_1.xml"))
      }
    }
  }

  it("strips bom in tei files read from GitHub") {
    val uri =
      "http://github:1234/git/blobs/ddffeb761e5158b41a3780cda22346978d2cd6bd"

    val responses = Seq(
      (
        HttpRequest(uri = Uri(uri)),
        HttpResponse(
          entity = HttpEntity(
            contentType = ContentTypes.`application/json`,
            readResource("github-blob-ddffeb7.json")
          )
        )
      )
    )

    val httpClient = new MemoryHttpClient(responses)

    withActorSystem { implicit ac =>
      val gitHubBlobReader = new GitHubBlobContentReader(httpClient)

      whenReady(gitHubBlobReader.getBlob(new URI(uri))) {
        assertXmlStringsAreEqual(_, readResource("Javanese_11.xml"))
      }
    }
  }

  it("handles error from github") {
    val uri = "http://github:1234/git/blobs/123456789qwertyu"

    val responses = Seq(
      (
        HttpRequest(uri = Uri(uri)),
        HttpResponse(
          status = StatusCodes.InternalServerError,
          entity = HttpEntity("<response>ERROR!</response>")
        )
      )
    )

    val httpClient = new MemoryHttpClient(responses)

    withActorSystem { implicit ac =>
      val gitHubBlobReader = new GitHubBlobContentReader(httpClient)

      whenReady(gitHubBlobReader.getBlob(new URI(uri)).failed) { result =>
        result shouldBe a[RuntimeException]
        result.getMessage should include("Server Error")
      }
    }
  }
}
