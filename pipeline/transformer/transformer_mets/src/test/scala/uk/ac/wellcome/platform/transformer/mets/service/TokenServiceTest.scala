package uk.ac.wellcome.platform.transformer.mets.service

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.akka.fixtures.Akka

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class TokenServiceTest
    extends FunSpec
    with BagsWiremock
    with Matchers
    with ScalaFutures
    with Akka
    with IntegrationPatience
    with Eventually {

  it("requests a token to the storage service") {
    withBagsService(8089, "localhost") {
      withActorSystem { implicit actorSystem =>
        withMaterializer(actorSystem) { implicit mat =>
          val tokenService = new TokenService(
            "http://localhost:8089",
            "client",
            "secret",
            "https://api.wellcomecollection.org/scope",
            100 milliseconds,
            100 millis)

          whenReady(tokenService.getToken) { token =>
            token shouldBe OAuth2BearerToken("token")
          }

          eventually {
            verify(
              moreThan(1),
              postRequestedFor(urlEqualTo("/oauth2/token"))
                .withRequestBody(matching(".*client_id=client.*"))
                .withRequestBody(matching(".*client_secret=secret.*"))
            )
          }
        }
      }
    }
  }

  it(
    "returns a failed future if it cannot get a token from the storage service") {
    withBagsService(8089, "localhost") {
      withActorSystem { implicit actorSystem =>
        withMaterializer(actorSystem) { implicit mat =>
          val tokenService = new TokenService(
            "http://localhost:8089",
            "wrongclient",
            "wrongsecret",
            "https://api.wellcomecollection.org/scope",
            100 milliseconds,
            100 millis)

          whenReady(tokenService.getToken.failed) { throwable =>
            throwable shouldBe a[Throwable]
          }

          eventually {
            verify(
              WireMock.exactly(3),
              postRequestedFor(urlEqualTo("/oauth2/token"))
                .withRequestBody(matching(".*client_id=wrongclient.*"))
                .withRequestBody(matching(".*client_secret=wrongsecret.*"))
            )
          }
        }
      }
    }
  }
}
