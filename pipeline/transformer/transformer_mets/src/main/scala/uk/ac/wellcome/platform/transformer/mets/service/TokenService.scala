package uk.ac.wellcome.platform.transformer.mets.service

import java.net.URI
import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.stream.Materializer
import com.github.dakatsuka.akka.http.oauth2.client.{Client, Config, GrantType}

import scala.concurrent.{ExecutionContext, Future}

class TokenService(url: String, clientId: String, secret: String,scope: String)(implicit actorSystem: ActorSystem, mat: Materializer, ec: ExecutionContext) {
  private val config = Config(
    clientId     = clientId,
    clientSecret = secret,
    site         = URI.create(url),
    tokenUrl = "/oauth2/token"
  )

  private val client = Client(config)
  private val token = new AtomicReference[OAuth2BearerToken](OAuth2BearerToken(""))

  def getNewToken(): Future[OAuth2BearerToken] =
    client.getAccessToken(GrantType.ClientCredentials,Map("scope" -> scope))
    .flatMap{
      case Right(accessToken) =>

        val newToken = OAuth2BearerToken(accessToken.accessToken)
        token.set(newToken)
        Future.successful(newToken)
      case Left(throwable) => Future.failed(throwable)
      }

  def getCurrentToken: OAuth2BearerToken = token.get()

}
