package weco.pipeline.calm_api_client

import org.apache.pekko.Done
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.{
  BasicHttpCredentials,
  Cookie,
  RawHeader
}
import org.apache.pekko.stream.{Materializer, RestartSettings}
import weco.catalogue.source_model.calm.CalmRecord
import weco.http.client.{AkkaHttpClient, HttpClient}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

// CalmApiClients provide methods to access the Calm API
// Implementors must provide a `request` method to take a
// CalmXmlRequest and return its Response.
trait CalmApiClient {
  protected def request[Request <: CalmXmlRequest: CalmHttpResponseParser](
    request: Request,
    cookie: Option[Cookie]
  ): Future[Request#Response]

  def search(
    query: CalmQueryBase,
    cookie: Option[Cookie] = None
  ): Future[CalmSession] =
    request(request = CalmSearchRequest(query), cookie = cookie)

  // We defer resolution of the summary parser so consumers can suppress fields
  def summary(pos: Int, cookie: Option[Cookie] = None)(
    implicit p: CalmHttpResponseParser[CalmSummaryRequest]
  ): Future[CalmRecord] =
    request(request = CalmSummaryRequest(pos), cookie = cookie)

  def abandon(cookie: Cookie): Future[Done] =
    request(request = CalmAbandonRequest, cookie = Some(cookie))
}

class HttpCalmApiClient(
  val client: HttpClient,
  url: String,
  username: String,
  password: String,
  minBackoff: FiniteDuration = 100 milliseconds,
  maxBackoff: FiniteDuration = 30 seconds,
  randomFactor: Double = 0.2,
  maxRestarts: Int = 10
)(implicit materializer: Materializer)
    extends CalmApiClient {
  private implicit val ec: ExecutionContext = materializer.executionContext

  private implicit val restartSettings: RestartSettings = RestartSettings(
    minBackoff = minBackoff,
    maxBackoff = maxBackoff,
    randomFactor = randomFactor
  ).withMaxRestarts(maxRestarts, minBackoff)

  def request[Request <: CalmXmlRequest](
    request: Request,
    cookie: Option[Cookie]
  )(implicit parse: CalmHttpResponseParser[Request]): Future[Request#Response] =
    RetryFuture {
      client
        .singleRequest(createHttpRequest(request, cookie))
        .map {
          resp =>
            resp.status match {
              case StatusCodes.OK => resp
              case status =>
                throw new Exception(s"Unexpected status: $status")
            }
        }
    }.recover {
      case lastException =>
        throw new RuntimeException(
          s"Max retries attempted when calling Calm API. Last failure was: ${lastException.getMessage}"
        )
    }.flatMap(parse.apply)

  private def createHttpRequest(
    xmlRequest: CalmXmlRequest,
    cookie: Option[Cookie]
  ): HttpRequest = {
    val request =
      HttpRequest(uri = url, method = HttpMethods.POST)
        .withEntity(ContentTypes.`text/xml(UTF-8)`, xmlRequest.xml.toString)
        .addCredentials(BasicHttpCredentials(username, password))
        .addHeader(
          RawHeader(
            "SOAPAction",
            s"http://ds.co.uk/cs/webservices/${xmlRequest.action}"
          )
        )
    cookie match {
      case Some(cookie) => request.addHeader(cookie)
      case None         => request
    }
  }
}

class AkkaHttpCalmApiClient(
  url: String,
  username: String,
  password: String,
  minBackoff: FiniteDuration = 100 milliseconds,
  maxBackoff: FiniteDuration = 30 seconds,
  randomFactor: Double = 0.2,
  maxRestarts: Int = 10
)(implicit actorSystem: ActorSystem)
    extends HttpCalmApiClient(
      client = new AkkaHttpClient(),
      url,
      username,
      password,
      minBackoff,
      maxBackoff,
      randomFactor,
      maxRestarts
    )
