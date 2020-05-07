package uk.ac.wellcome.calm_adapter

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.stream.Materializer
import akka.http.scaladsl._
import akka.http.scaladsl.model._

trait CalmHttpClient {

  def apply(request: HttpRequest): Future[HttpResponse]
}

abstract class CalmHttpClientWithBackoff(
  minBackoff: FiniteDuration = 100 milliseconds,
  maxBackoff: FiniteDuration = 30 seconds,
  randomFactor: Double = 0.2,
  maxRestarts: Int = 10)(implicit
                         ec: ExecutionContext,
                         materializer: Materializer)
    extends CalmHttpClient {

  def apply(request: HttpRequest): Future[HttpResponse] =
    RestartSource
      .onFailuresWithBackoff(
        minBackoff = minBackoff,
        maxBackoff = maxBackoff,
        randomFactor = randomFactor,
        maxRestarts = maxRestarts
      ) { () =>
        Source
          .future(singleRequest(request))
          .map { resp =>
            resp.status match {
              case StatusCodes.OK => resp
              case status =>
                throw new Exception(s"Unexpected status from CALM API: $status")
            }
          }
      }
      .runWith(Sink.head)
      .recover {
        case _ =>
          throw new Exception("Max retries attempted when calling Calm API")
      }

  def singleRequest(request: HttpRequest): Future[HttpResponse]
}

/** HTTP client using akka-streams with exponential backoff for status codes
  * deemed recoverable.
  */
class CalmAkkaHttpClient(minBackoff: FiniteDuration = 100 milliseconds,
                         maxBackoff: FiniteDuration = 30 seconds,
                         randomFactor: Double = 0.2,
                         maxRestarts: Int = 10)(implicit
                                                ec: ExecutionContext,
                                                actorSystem: ActorSystem,
                                                materializer: Materializer)
    extends CalmHttpClientWithBackoff(
      minBackoff,
      maxBackoff,
      randomFactor,
      maxRestarts) {

  def singleRequest(request: HttpRequest): Future[HttpResponse] =
    Http().singleRequest(request)
}
