package uk.ac.wellcome.mets

import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import grizzled.slf4j.Logging
import io.circe.generic.auto._

import scala.concurrent.{ExecutionContext, Future}

class BagsRetriever(url: String)(
  implicit
  ec: ExecutionContext,
  actorSystem: ActorSystem,
  actorMaterializer: ActorMaterializer) extends Logging {

  def flow: Flow[StorageUpdate, Bag, _] =
    Flow[StorageUpdate]
      .mapAsync(1) { update =>
        val request = HttpRequest(uri = s"$url/${update.space}/${update.bagId}")
        Http().singleRequest(request)
      }
      .mapAsync(1)(resp => responseToBag(resp))
      .collect { case Some(bag) => bag }

  private def responseToBag(response: HttpResponse): Future[Option[Bag]] = {
    debug(s"Received response ${response.status}")
    response.status match {
      case StatusCodes.OK =>
        Unmarshal(response.entity)
          .to[Bag]
          .map(Some(_))
          .recover { case exc =>
            error(s"Could not decode bag", exc)
            None
          }
      case status =>
        if (status != StatusCodes.NotFound)
          error(s"Received $status from storage service")
        Future.successful(None)
    }
  }
}
