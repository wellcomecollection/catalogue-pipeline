package weco.pipeline.calm_api_client

import akka.stream.{Materializer, RestartSettings}
import akka.stream.scaladsl.{RestartSource, Sink, Source}

import scala.concurrent.Future
import scala.concurrent.duration._

object RetryFuture {

  lazy val defaultRestartSettings: RestartSettings = RestartSettings(
    minBackoff = 100 milliseconds,
    maxBackoff = 30 seconds,
    randomFactor = 0.2
  ).withMaxRestarts(10, 100 milliseconds)

  def apply[T](f: => Future[T])(implicit
    mat: Materializer,
    settings: RestartSettings = defaultRestartSettings
  ): Future[T] =
    RestartSource
      .onFailuresWithBackoff(settings) { () =>
        Source.future(f)
      }
      .runWith(Sink.head)

}
