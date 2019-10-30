package uk.ac.wellcome.platform.transformer.mets.service

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import org.mockito.Mockito
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Inside, Matchers}
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.platform.transformer.mets.model.{Bag, BagFile, BagLocation, BagManifest}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BagsRetrieverTest
    extends FunSpec
    with Matchers
    with BagsWiremock
    with Inside
    with ScalaFutures
    with Akka
    with IntegrationPatience with MockitoSugar{

  it("gets a bag from the storage service") {
    withBagsService(8089, "localhost") {
      withActorSystem { implicit actorSystem =>
        withMaterializer(actorSystem) { implicit materializer =>
          val bagsRetriever =
            new BagsRetriever("http://localhost:8089/storage/v1/bags", new TokenService("http://localhost:8089", "client", "secret", "https://api.wellcomecollection.org/scope"))
          whenReady(bagsRetriever.getBag("digitised", "b30246039")) {
            maybeBag =>
              inside(maybeBag) {
                case Some(
                    Bag(_, BagManifest(files), BagLocation(bucket, path))) =>
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
    withBagsService(8089, "localhost") {
      withActorSystem { implicit actorSystem =>
        withMaterializer(actorSystem) { implicit materializer =>
          val bagsRetriever =
            new BagsRetriever("http://localhost:8089/storage/v1/bags", new TokenService("http://localhost:8089", "client", "secret", "https://api.wellcomecollection.org/scope"))
          whenReady(bagsRetriever.getBag("digitised", "not-existing")) {
            maybeBag =>
              maybeBag shouldBe None
          }
        }
      }
    }
  }

  it("retries only once if the storage service responds with unauthorized") {
    withBagsService(8089, "localhost") {
      withActorSystem { implicit actorSystem =>
        withMaterializer(actorSystem) { implicit materializer =>
          val tokenService = mock[TokenService]
          Mockito.when(tokenService.getCurrentToken).thenReturn(OAuth2BearerToken("not-valid-token"))
          Mockito.when(tokenService.getNewToken()).thenReturn(Future.successful(OAuth2BearerToken("not-valid-token")))
          val bagsRetriever =
            new BagsRetriever("http://localhost:8089/storage/v1/bags", tokenService)
          whenReady(bagsRetriever.getBag("digitised", "b30246039").failed) {e =>
            e shouldBe a[Throwable]
            verify(2, getRequestedFor(
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
          stubFor(
            get(urlMatching("/storage/v1/bags/digitised/this-shall-crash"))
              .willReturn(aResponse().withStatus(500)))

          val bagsRetriever =
            new BagsRetriever("http://localhost:8089/storage/v1/bags", new TokenService("http://localhost:8089", "client", "secret", "https://api.wellcomecollection.org/scope"))

          whenReady(
            bagsRetriever.getBag("digitised", "this-shall-crash").failed) { e =>
            e shouldBe a[Throwable]
          }
        }
      }
    }
  }

  it("returns a failed future if the storage service response has a fault") {
    withBagsService(8089, "localhost") {
      withActorSystem { implicit actorSystem =>
        withMaterializer(actorSystem) { implicit materializer =>
          stubFor(
            get(urlMatching("/storage/v1/bags/digitised/this-will-fault"))
              .willReturn(aResponse()
                .withStatus(200)
                .withFault(Fault.CONNECTION_RESET_BY_PEER)))
          val bagsRetriever =
            new BagsRetriever("http://localhost:8089/storage/v1/bags", new TokenService("http://localhost:8089", "client", "secret", "https://api.wellcomecollection.org/scope"))

          whenReady(bagsRetriever.getBag("digitised", "this-will-fault").failed) {
            e =>
              e shouldBe a[Throwable]
          }
        }

      }
    }
  }
}
