package weco.pipeline.mets_adapter.http

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.headers.{
  Authorization,
  BasicHttpCredentials,
  OAuth2BearerToken
}
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshal}
import weco.json.JsonUtil._
import weco.http.client.{HttpClient, HttpGet, TokenExchange}
import weco.http.json.CirceMarshalling

import java.time.Instant
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class StorageServiceOauthHttpClient(
  underlying: HttpClient,
  val baseUri: Uri,
  val tokenUri: Uri,
  val credentials: BasicHttpCredentials,
  val expiryGracePeriod: Duration = 60.seconds
)(implicit val system: ActorSystem, val ec: ExecutionContext)
    extends HttpClient
    with HttpGet
    with TokenExchange[BasicHttpCredentials, OAuth2BearerToken] {

  implicit val um: FromEntityUnmarshaller[StorageServiceAccessToken] =
    CirceMarshalling.fromDecoder[StorageServiceAccessToken]

  override protected def getNewToken(
    credentials: BasicHttpCredentials
  ): Future[(OAuth2BearerToken, Instant)] =
    for {
      tokenResponse <- underlying.singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = tokenUri,
          headers = List(Authorization(credentials)),
          entity = HttpEntity(
            contentType = ContentTypes.`application/x-www-form-urlencoded`,
            "grant_type=client_credentials"
          )
        )
      )

      accessToken <- tokenResponse.status match {
        case StatusCodes.OK =>
          Unmarshal(tokenResponse).to[StorageServiceAccessToken]
        case code =>
          Unmarshal(tokenResponse).to[String].flatMap {
            resp =>
              Future.failed(
                new Throwable(
                  s"Unexpected status code $code from $tokenUri: $resp"
                )
              )
          }
      }

      result = (
        OAuth2BearerToken(accessToken.accessToken),
        Instant.now().plusSeconds(accessToken.expiresIn)
      )
    } yield result

  override def singleRequest(request: HttpRequest): Future[HttpResponse] =
    for {
      token <- getToken(credentials)

      authenticatedRequest = {
        // We're going to set our own Authorization header on this request
        // using the token, so there shouldn't be one already.
        //
        // Are multiple Authorization headers allowed by HTTP?  It doesn't matter,
        // it's not something we should be doing.
        val existingAuthHeaders = request.headers.collect {
          case auth: Authorization => auth
        }
        require(
          existingAuthHeaders.isEmpty,
          s"HTTP request already has auth headers: $request"
        )

        request.withHeaders(request.headers :+ Authorization(token))
      }

      response <- underlying.singleRequest(authenticatedRequest)
    } yield response
}
