package weco.catalogue.tei.github

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import weco.catalogue.tei.github.fixtures.Wiremock

import java.net.URI
import java.time.ZonedDateTime

class GitHubRetrieverTest
    extends AnyFunSpec
    with Matchers
    with Akka
    with ScalaFutures
    with IntegrationPatience
    with Wiremock {
  it("retrieves files modified in a time window") {
    withWiremock("localhost") { port =>
      withActorSystem { implicit ac =>
        val retriever = GitHubRetriever(s"http://localhost:$port", "master")
        whenReady(
          retriever.getFiles(
            Window(
              ZonedDateTime.parse("2021-05-05T10:00:00Z"),
              ZonedDateTime.parse("2021-05-07T18:00:00Z")))) { result =>
          result should contain theSameElementsAs List(
            new URI(
              "https://github.com/wellcomecollection/wellcome-collection-tei/raw/1e394d3186b6a8ed5f0fa8af33b99bdc59d7c544/Arabic/WMS_Arabic_49.xml"),
            new URI(
              "https://github.com/wellcomecollection/wellcome-collection-tei/raw/481fa2d65ec2b44a4293823aa86b6f5e455a7c0d/Arabic/WMS_Arabic_46.xml"),
            new URI(
              "https://github.com/wellcomecollection/wellcome-collection-tei/raw/db7581026bb9149330225dc2b9411202b3cd6894/Arabic/WMS_Arabic_62.xml")
          )
        }
      }
    }
  }
  it("gets commits from a merge") {
    withWiremock("localhost") { port =>
      withActorSystem { implicit ac =>
        val retriever = GitHubRetriever(s"http://localhost:$port", "master")
        whenReady(
          retriever.getFiles(
            Window(
              ZonedDateTime.parse("2021-05-05T10:00:00Z"),
              ZonedDateTime.parse("2021-05-07T18:00:00Z")))) { result =>
          fail()
        }
      }
    }
  }
  it("deduplicates the list of URIs") {
    fail()
  }
  it("handles error codes other than 200") {
    fail()
  }
}
