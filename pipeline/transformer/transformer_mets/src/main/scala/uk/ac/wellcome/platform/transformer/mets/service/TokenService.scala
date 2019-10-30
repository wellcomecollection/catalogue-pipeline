package uk.ac.wellcome.platform.transformer.mets.service

import java.net.URI
import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
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
  private val token = new AtomicReference[String]("")
  def getNewToken(): Future[Either[Throwable, String]] =
    client.getAccessToken(GrantType.ClientCredentials,Map("scope" -> scope))
    .map(either =>
      either.map(accessToken => {
        token.set(accessToken.accessToken)
        accessToken.accessToken
      }))

  def getCurrentToken: String = token.get()

}
