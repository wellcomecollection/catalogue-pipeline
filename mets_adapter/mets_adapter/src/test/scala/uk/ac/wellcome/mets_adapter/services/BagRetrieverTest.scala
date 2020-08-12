package uk.ac.wellcome.mets_adapter.services

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import org.mockito.Mockito
import org.scalatest.Inside
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.mets_adapter.models._
import uk.ac.wellcome.mets_adapter.fixtures.BagsWiremock

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
          case Bag(_, BagManifest(files), BagLocation(bucket, path), _) =>
            verify(
              moreThanOrExactly(1),
              postRequestedFor(urlEqualTo("/oauth2/token"))
                .withRequestBody(matching(".*client_id=client.*"))
                .withRequestBody(matching(".*client_secret=secret.*"))
            )

            verify(
              getRequestedFor(
                urlEqualTo("/storage/v1/bags/digitised/b30246039")))
            files.head shouldBe BagFile(
              "data/b30246039.xml",
              "v1/data/b30246039.xml")
            bucket shouldBe "wellcomecollection-storage"
            path shouldBe "digitised/b30246039"
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
    withActorSystem { implicit actorSystem =>
      val tokenService = mock[TokenService]
      Mockito
        .when(tokenService.getToken)
        .thenReturn(Future.successful(OAuth2BearerToken("not-valid-token")))
      withBagRetriever(tokenService) { bagRetriever =>
        whenReady(getBag(bagRetriever, "digitised", "b30246039").failed) { e =>
          e shouldBe a[Throwable]
          verify(
            1,
            getRequestedFor(urlEqualTo("/storage/v1/bags/digitised/b30246039")))
        }
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

  def withBagRetriever[R](tokenService: TokenService)(
    testWith: TestWith[BagRetriever, R])(implicit actorSystem: ActorSystem): R =
    withBagsService("localhost") { port =>
      testWith(
        new HttpBagRetriever(
          s"http://localhost:$port/storage/v1/bags",
          tokenService)
      )
    }

  def withBagRetriever[R](testWith: TestWith[BagRetriever, R])(
    implicit actorSystem: ActorSystem): Unit =
    withBagsService("localhost") { port =>
      withTokenService(
        s"http://localhost:$port",
        "client",
        "secret",
        "https://api.wellcomecollection.org/scope") { tokenService =>
        testWith(
          new HttpBagRetriever(
            s"http://localhost:$port/storage/v1/bags",
            tokenService)
        )
      }
    }

  def withTokenService[R](url: String,
                          clientId: String,
                          secret: String,
                          scope: String)(testWith: TestWith[TokenService, R])(
    implicit actorSystem: ActorSystem): R =
    testWith(new TokenService(url, clientId, secret, scope))
}
