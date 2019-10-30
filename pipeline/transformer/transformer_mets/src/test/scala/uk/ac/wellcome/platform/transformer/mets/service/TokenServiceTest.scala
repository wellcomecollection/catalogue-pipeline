package uk.ac.wellcome.platform.transformer.mets.service

import com.github.tomakehurst.wiremock.client.BasicCredentials
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpec, Matchers}

class TokenServiceTest extends FunSpec with BagsWiremock with Matchers with ScalaFutures{
  it("requests a token to the storage service"){
    withBagsService(8089, "localhost"){
      val tokenService = new TokenService("http://localhost:8089/oauth2/token", "client", "secret")

      whenReady(tokenService.getNewToken()) { token =>
        token shouldBe "token"

        verify(postRequestedFor(
          urlEqualTo("/oauth2/token"))
          .withBasicAuth(new BasicCredentials("client", "secret")))
      }
    }
  }

}
