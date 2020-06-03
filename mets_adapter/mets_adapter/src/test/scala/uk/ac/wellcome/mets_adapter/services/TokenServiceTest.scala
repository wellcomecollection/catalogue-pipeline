package uk.ac.wellcome.mets_adapter.services

import scala.concurrent.ExecutionContext.Implicits.global
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.mets_adapter.fixtures.BagsWiremock

class TokenServiceTest
    extends AnyFunSpec
    with BagsWiremock
    with Matchers
    with ScalaFutures
    with Akka
    with IntegrationPatience
    with Eventually {

  it("requests a token to the storage service") {
    withBagsService("localhost") { port =>
      withActorSystem { implicit actorSystem =>
        val tokenService = new TokenService(
          url = s"http://localhost:$port",
          clientId = "client",
          secret = "secret",
          scope = "https://api.wellcomecollection.org/scope")

        whenReady(tokenService.getToken) {
          _ shouldBe OAuth2BearerToken("token")
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

  it(
    "returns a failed future if it cannot get a token from the storage service") {
    withBagsService("localhost") { port =>
      withActorSystem { implicit actorSystem =>
        val tokenService = new TokenService(
          s"http://localhost:$port",
          "wrongclient",
          "wrongsecret",
          "https://api.wellcomecollection.org/scope")

        whenReady(tokenService.getToken.failed) {
          _ shouldBe a[Throwable]
        }
      }
    }
  }
}
