package uk.ac.wellcome.mets_adapter.services

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import org.scalatest.Inside
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.mets_adapter.fixtures.BagsWiremock
import uk.ac.wellcome.mets_adapter.models._
import weco.catalogue.mets_adapter.http.StorageServiceOauthHttpClient
import weco.http.client.{AkkaHttpClient, HttpGet, HttpPost, MemoryHttpClient}

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BagRetrieverTest
    extends AnyFunSpec
    with Matchers
    with BagsWiremock
    with Inside
    with ScalaFutures
    with Akka
    with IntegrationPatience
    with MockitoSugar {

  it("gets a bag from the storage service") {
    withActorSystem { implicit actorSystem =>
      withBagRetriever { bagRetriever =>
        whenReady(getBag(bagRetriever, "digitised", "b30246039")) {
          case Bag(
              _,
              BagManifest(files),
              BagLocation(bucket, path),
              _,
              createdDate) =>
            files.head shouldBe BagFile(
              "data/b30246039.xml",
              "v1/data/b30246039.xml")
            bucket shouldBe "wellcomecollection-storage"
            path shouldBe "digitised/b30246039"
            createdDate shouldBe Instant.parse("2019-09-14T02:25:40.532998Z")
        }
      }
    }
  }

  it("fails if the bag does not exist in the storage service") {
    withActorSystem { implicit actorSystem =>
      withBagRetriever { bagRetriever =>
        whenReady(getBag(bagRetriever, "digitised", "not-existing").failed) {
          e =>
            e shouldBe a[Throwable]
            verify(
              1,
              getRequestedFor(
                urlEqualTo("/storage/v1/bags/digitised/not-existing")))
        }
      }
    }
  }

  it("does not retry if the storage service responds with unauthorized") {
    val responses = Seq(
      (
        HttpRequest(uri = Uri("http://storage:1234/bags/digitised/b30246039")),
        HttpResponse(status = StatusCodes.Unauthorized)
      )
    )

    val client = new MemoryHttpClient(responses) with HttpGet {
      override val baseUri: Uri = Uri("http://storage:1234/bags")
    }

    withActorSystem { implicit actorSystem =>
      val retriever = new HttpBagRetriever(client)

      val future = retriever.getBag(space = "digitised", externalIdentifier = "b30246039")

      whenReady(future.failed) {
        _.getMessage should startWith("Failed to authorize with storage service")
      }
    }
  }

  it("returns a failed future if the storage service responds with 500") {
    withActorSystem { implicit actorSystem =>
      withBagRetriever { bagRetriever =>
        stubFor(
          get(urlMatching("/storage/v1/bags/digitised/this-shall-crash"))
            .willReturn(aResponse().withStatus(500)))
        whenReady(getBag(bagRetriever, "digitised", "this-shall-crash").failed) {
          _ shouldBe a[Throwable]
        }
      }
    }
  }

  it("returns a failed future if the storage service response has a fault") {
    withActorSystem { implicit actorSystem =>
      withBagRetriever { bagRetriever =>
        stubFor(
          get(urlMatching("/storage/v1/bags/digitised/this-will-fault"))
            .willReturn(
              aResponse()
                .withStatus(200)
                // Simulate an immediately returning Fault
                // Fault.CONNECTION_RESET_BY_PEER can cause an extended wait
                // which can cause this test to fail in some environments
                .withFault(Fault.RANDOM_DATA_THEN_CLOSE)))
        whenReady(getBag(bagRetriever, "digitised", "this-will-fault").failed) {
          _ shouldBe a[Throwable]
        }
      }
    }
  }

  def getBag(bagRetriever: BagRetriever,
             space: String,
             externalIdentifier: String): Future[Bag] =
    bagRetriever.getBag(space = space, externalIdentifier = externalIdentifier)

  def withBagRetriever[R](testWith: TestWith[BagRetriever, R])(
    implicit actorSystem: ActorSystem): Unit =
    withBagsService("localhost") { port =>
      val client = new AkkaHttpClient() with HttpGet with HttpPost {
        override val baseUri: Uri = Uri(s"http://localhost:$port/storage/v1/bags")
      }

      val oauthClient = new StorageServiceOauthHttpClient(
        client,
        tokenUri = Uri(s"http://localhost:$port/oauth2/token"),
        credentials = BasicHttpCredentials("client", "secret")
      )

      testWith(new HttpBagRetriever(oauthClient))
    }
}
