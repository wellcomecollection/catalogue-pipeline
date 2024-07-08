package weco.pipeline.inference_manager.fixtures

import java.util.concurrent.ConcurrentHashMap
import org.apache.pekko.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, Uri}
import org.apache.pekko.stream.scaladsl.Flow
import weco.fixtures.TestWith
import weco.pipeline.inference_manager.services.RequestPoolFlow

import scala.collection.JavaConverters._
import scala.collection._
import scala.util.Try

class RequestPoolMock[T, Ctx](matchResponse: Uri => Option[HttpResponse]) {
  val requests: concurrent.Map[HttpRequest, Unit] =
    new ConcurrentHashMap[HttpRequest, Unit].asScala

  def pool: RequestPoolFlow[T, Ctx] =
    Flow[(HttpRequest, (T, Ctx))].map {
      case (request, messagePair) =>
        requests.put(request, ())
        Try(
          matchResponse(request.uri)
            .getOrElse(HttpResponse(status = StatusCodes.NotFound))
        ) -> messagePair
    }
}

object RequestPoolMock {
  def apply[T, Ctx](matchResponse: String => Option[HttpResponse]) =
    new RequestPoolMock[T, Ctx](uri => matchResponse(uri.toString))
}

trait RequestPoolFixtures {
  def withRequestPool[T, Ctx, R](matchResponse: String => Option[HttpResponse])(
    testWith: TestWith[RequestPoolMock[T, Ctx], R]
  ): R =
    testWith(RequestPoolMock[T, Ctx](matchResponse))
}
