package weco.catalogue.tei.id_extractor

import org.apache.commons.io.IOUtils
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import weco.catalogue.tei.id_extractor.fixtures.Wiremock

import java.net.URI
import java.nio.charset.StandardCharsets

class GitHubBlobReaderTest extends AnyFunSpec with Wiremock with ScalaFutures with Matchers with Akka with IntegrationPatience{
  it("reads a blob from GitHub"){
    withWiremock("localhost") { port =>
      withActorSystem { implicit ac =>
        val uri = new URI(s"http://localhost:$port/git/blobs/2e6b5fa45462510d5549b6bcf2bbc8b53ae08aed")
        val gitHubBlobReader = new GitHubBlobReader()
        whenReady(gitHubBlobReader.getBlob(uri)) { result =>
          result shouldBe IOUtils.resourceToString("/WMS_Arabic_1.xml", StandardCharsets.UTF_8)
        }
      }
    }
  }
  it("handles error from github"){
    fail()
  }
}
