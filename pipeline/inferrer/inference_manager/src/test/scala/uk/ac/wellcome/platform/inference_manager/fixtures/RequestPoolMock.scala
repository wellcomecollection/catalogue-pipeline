package uk.ac.wellcome.platform.inference_manager.fixtures

import java.util.concurrent.ConcurrentHashMap

import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.stream.scaladsl.Flow
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.inference_manager.services.{
  MessagePair,
  RequestPoolFlow
}

import scala.collection.JavaConverters._
import scala.collection._
import scala.util.Try

class RequestPoolMock[T](matchResponse: Uri => Option[HttpResponse]) {
  val requests: concurrent.Map[HttpRequest, Unit] =
    new ConcurrentHashMap[HttpRequest, Unit].asScala

  def pool: RequestPoolFlow[T] =
    Flow[(HttpRequest, MessagePair[T])].map {
      case (request, messagePair) =>
        requests.put(request, ())
        Try(
          matchResponse(request.uri)
            .getOrElse(HttpResponse(status = StatusCodes.NotFound))
        ) -> messagePair
    }
}

object RequestPoolMock {
  def apply[T](matchResponse: String => Option[HttpResponse]) =
    new RequestPoolMock[T](uri => matchResponse(uri.toString))
}

trait RequestPoolFixtures {
  def withResponsePool[T, R](matchResponse: String => Option[HttpResponse])(
    testWith: TestWith[RequestPoolMock[T], R]): R =
    testWith(RequestPoolMock[T](matchResponse))
}
