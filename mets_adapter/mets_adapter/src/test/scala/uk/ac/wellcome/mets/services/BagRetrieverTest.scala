package uk.ac.wellcome.mets_adapter.services

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.stream.ActorMaterializer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import org.mockito.Mockito
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Inside, Matchers}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.mets_adapter.models._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class BagRetrieverTest
    extends FunSpec
    with Matchers
    with BagsWiremock
    with Inside
    with ScalaFutures
    with Akka
    with IntegrationPatience
    with MockitoSugar {

  it("gets a bag from the storage service") {
    withActorSystem { implicit actorSystem =>
      withMaterializer(actorSystem) { implicit materializer =>
        withBagRetriever(0 milliseconds, 1 milliseconds) { bagRetriever =>
          whenReady(getBag(bagRetriever, "digitised", "b30246039")) {
            maybeBag =>
              inside(maybeBag) {
                case Some(
                    Bag(_, BagManifest(files), BagLocation(bucket, path))) =>
                  verify(
                    moreThanOrExactly(1),
                    postRequestedFor(urlEqualTo("/oauth2/token"))
                      .withRequestBody(matching(".*client_id=client.*"))
                      .withRequestBody(matching(".*client_secret=secret.*"))
                  )

                  verify(getRequestedFor(
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
    }
  }

  it("returns a none if the bag does not exist in the storage service") {
    withActorSystem { implicit actorSystem =>
      withMaterializer(actorSystem) { implicit materializer =>
        withBagRetriever(0 milliseconds, 100 milliseconds) { bagRetriever =>
          whenReady(getBag(bagRetriever, "digitised", "not-existing")) {
            maybeBag =>
              maybeBag shouldBe None
          }
        }
      }
    }
  }

  it("does not retry if the storage service responds with unauthorized") {
    withActorSystem { implicit actorSystem =>
      withMaterializer(actorSystem) { implicit materializer =>
        val tokenService = mock[TokenService]
        Mockito
          .when(tokenService.getToken)
          .thenReturn(Future.successful(OAuth2BearerToken("not-valid-token")))
        withBagRetriever(tokenService) { bagRetriever =>
          whenReady(getBag(bagRetriever, "digitised", "b30246039").failed) {
            e =>
              e shouldBe a[Throwable]
              verify(
                1,
                getRequestedFor(
                  urlEqualTo("/storage/v1/bags/digitised/b30246039")))
          }
        }
      }
    }
  }

  it("returns a failed future if the storage service responds with 500") {
    withActorSystem { implicit actorSystem =>
      withMaterializer(actorSystem) { implicit materializer =>
        withBagRetriever(100 milliseconds, 100 milliseconds) { bagRetriever =>
          stubFor(
            get(urlMatching("/storage/v1/bags/digitised/this-shall-crash"))
              .willReturn(aResponse().withStatus(500)))
          whenReady(
            getBag(bagRetriever, "digitised", "this-shall-crash").failed) { e =>
            e shouldBe a[Throwable]
          }
        }
      }
    }
  }

  it("returns a failed future if the storage service response has a fault") {
    withActorSystem { implicit actorSystem =>
      withMaterializer(actorSystem) { implicit materializer =>
        withBagRetriever(100 milliseconds, 100 milliseconds) { bagRetriever =>
          stubFor(
            get(urlMatching("/storage/v1/bags/digitised/this-will-fault"))
              .willReturn(aResponse()
                .withStatus(200)
                .withFault(Fault.CONNECTION_RESET_BY_PEER)))
          whenReady(getBag(bagRetriever, "digitised", "this-will-fault").failed) {
            e =>
              e shouldBe a[Throwable]
          }
        }
      }
    }
  }

  def getBag(bagRetriever: BagRetriever,
             space: String,
             bagId: String): Future[Option[Bag]] =
    bagRetriever.getBag(StorageUpdate(space, bagId))

  def withBagRetriever[R](tokenService: TokenService)(
    testWith: TestWith[BagRetriever, R])(implicit actorSystem: ActorSystem,
                                         materializer: ActorMaterializer) =
    withBagsService("localhost") { port =>
      testWith(
        new BagRetriever(
          s"http://localhost:$port/storage/v1/bags",
          tokenService)
      )
    }

  def withBagRetriever[R](
    initialDelay: FiniteDuration,
    interval: FiniteDuration)(testWith: TestWith[BagRetriever, R])(
    implicit actorSystem: ActorSystem,
    materializer: ActorMaterializer) =
    withBagsService("localhost") { port =>
      withTokenService(
        s"http://localhost:$port",
        "client",
        "secret",
        "https://api.wellcomecollection.org/scope")(initialDelay, interval) {
        tokenService =>
          testWith(
            new BagRetriever(
              s"http://localhost:$port/storage/v1/bags",
              tokenService)
          )
      }
    }

  def withTokenService[R](
    url: String,
    clientId: String,
    secret: String,
    scope: String)(initialDelay: FiniteDuration, interval: FiniteDuration)(
    testWith: TestWith[TokenService, R])(implicit actorSystem: ActorSystem,
                                         materializer: ActorMaterializer) {
    testWith(
      new TokenService(url, clientId, secret, scope, initialDelay, interval))
  }
}
