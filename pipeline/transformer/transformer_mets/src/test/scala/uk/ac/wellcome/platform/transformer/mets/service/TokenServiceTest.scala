package uk.ac.wellcome.platform.transformer.mets.service

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.akka.fixtures.Akka

import scala.concurrent.ExecutionContext.Implicits.global

class TokenServiceTest extends FunSpec with BagsWiremock with Matchers with ScalaFutures with Akka with IntegrationPatience{
  it("requests a token to the storage service"){
    withBagsService(8089, "localhost"){
      withActorSystem { implicit actorSystem =>
        withMaterializer(actorSystem) { implicit mat =>
          val tokenService = new TokenService("http://localhost:8089", "client", "secret", "https://api.wellcomecollection.org/scope")

          whenReady(tokenService.getNewToken()) { token =>

            verify(postRequestedFor(
                urlEqualTo("/oauth2/token"))
              .withRequestBody(matching(".*client_id=client.*"))
              .withRequestBody(matching(".*client_secret=secret.*"))
            )

            token shouldBe Right("token")
          }
        }
      }
    }
  }

}
