package uk.ac.wellcome.platform.transformer.mets.service

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.stream.Materializer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import org.mockito.Mockito
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Inside, Matchers}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.transformer.mets.model.{
  Bag,
  BagFile,
  BagLocation,
  BagManifest
}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class BagsRetrieverTest
    extends FunSpec
    with Matchers
    with BagsWiremock
    with Inside
    with ScalaFutures
    with Akka
    with IntegrationPatience
    with MockitoSugar {

  it("gets a bag from the storage service") {
    withBagsService(8089, "localhost") {
      withActorSystem { implicit actorSystem =>
        withMaterializer(actorSystem) { implicit materializer =>
          withTokenService(
            "http://localhost:8089",
            "client",
            "secret",
            "https://api.wellcomecollection.org/scope")(
            0 milliseconds,
            1 milliseconds) { tokenService =>
            val bagsRetriever =
              new BagsRetriever(
                "http://localhost:8089/storage/v1/bags",
                tokenService)
            whenReady(bagsRetriever.getBag("digitised", "b30246039")) {
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
  }

  it("returns a none if the bag does not exist in the storage service") {
    withBagsService(8089, "localhost") {
      withActorSystem { implicit actorSystem =>
        withMaterializer(actorSystem) { implicit materializer =>
          withTokenService(
            "http://localhost:8089",
            "client",
            "secret",
            "https://api.wellcomecollection.org/scope")(
            0 milliseconds,
            100 milliseconds) { tokenService =>
            val bagsRetriever =
              new BagsRetriever(
                "http://localhost:8089/storage/v1/bags",
                tokenService)
            whenReady(bagsRetriever.getBag("digitised", "not-existing")) {
              maybeBag =>
                maybeBag shouldBe None
            }
          }
        }
      }
    }
  }

  it("does not retry if the storage service responds with unauthorized") {
    withBagsService(8089, "localhost") {
      withActorSystem { implicit actorSystem =>
        withMaterializer(actorSystem) { implicit materializer =>
          val tokenService = mock[TokenService]
          Mockito
            .when(tokenService.getToken)
            .thenReturn(Future.successful(OAuth2BearerToken("not-valid-token")))
          val bagsRetriever =
            new BagsRetriever(
              "http://localhost:8089/storage/v1/bags",
              tokenService)
          whenReady(bagsRetriever.getBag("digitised", "b30246039").failed) {
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
    withBagsService(8089, "localhost") {
      withActorSystem { implicit actorSystem =>
        withMaterializer(actorSystem) { implicit materializer =>
          withTokenService(
            "http://localhost:8089",
            "client",
            "secret",
            "https://api.wellcomecollection.org/scope")(
            100 milliseconds,
            100 milliseconds) { tokenService =>
            stubFor(
              get(urlMatching("/storage/v1/bags/digitised/this-shall-crash"))
                .willReturn(aResponse().withStatus(500)))

            val bagsRetriever =
              new BagsRetriever(
                "http://localhost:8089/storage/v1/bags",
                tokenService)

            whenReady(
              bagsRetriever.getBag("digitised", "this-shall-crash").failed) {
              e =>
                e shouldBe a[Throwable]
            }
          }
        }
      }
    }
  }

  it("returns a failed future if the storage service response has a fault") {
    withBagsService(8089, "localhost") {
      withActorSystem { implicit actorSystem =>
        withMaterializer(actorSystem) { implicit materializer =>
          withTokenService(
            "http://localhost:8089",
            "client",
            "secret",
            "https://api.wellcomecollection.org/scope")(
            100 milliseconds,
            100 milliseconds) { tokenService =>
            stubFor(
              get(urlMatching("/storage/v1/bags/digitised/this-will-fault"))
                .willReturn(aResponse()
                  .withStatus(200)
                  .withFault(Fault.CONNECTION_RESET_BY_PEER)))
            val bagsRetriever =
              new BagsRetriever(
                "http://localhost:8089/storage/v1/bags",
                tokenService)

            whenReady(
              bagsRetriever.getBag("digitised", "this-will-fault").failed) {
              e =>
                e shouldBe a[Throwable]
            }
          }
        }
      }
    }
  }

  def withTokenService[R](
    url: String,
    clientId: String,
    secret: String,
    scope: String)(initialDelay: FiniteDuration, interval: FiniteDuration)(
    testWith: TestWith[TokenService, R])(implicit actorSystem: ActorSystem,
                                         materializer: Materializer) {
    testWith(
      new TokenService(url, clientId, secret, scope, initialDelay, interval))
  }
}
