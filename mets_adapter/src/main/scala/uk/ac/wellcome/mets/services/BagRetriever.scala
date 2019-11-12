package uk.ac.wellcome.mets.services

import scala.concurrent.{ExecutionContext, Future}
import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.stream.ActorMaterializer
import grizzled.slf4j.Logging
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import uk.ac.wellcome.mets.models._

class BagRetriever(url: String, tokenService: TokenService, concurrentConnections: Int = 6)(
  implicit
  ec: ExecutionContext,
  actorSystem: ActorSystem,
  actorMaterializer: ActorMaterializer) extends Logging with FailFastCirceSupport {

  def flow: Flow[StorageUpdate, Option[Bag], _] =
    Flow[StorageUpdate]
      .mapAsync(1) { update =>
        tokenService.getToken.map(token => (token, update))
      }
      .mapAsync(concurrentConnections) { case (token, update) =>
        Http().singleRequest(generateRequest(update, token))
      }
      .mapAsync(2)(resp => responseToBag(resp))
  
  private def generateRequest(update: StorageUpdate, token: OAuth2BearerToken): HttpRequest =
   HttpRequest(uri = s"$url/${update.space}/${update.bagId}")
     .addHeader(Authorization(token))

  private def responseToBag(response: HttpResponse): Future[Option[Bag]] =
    response.status match {
      case StatusCodes.OK => parseResponse(response)
      case status =>
        if (status == StatusCodes.Unauthorized)
          error(s"Failed to authorize with storage service")
        else if (status != StatusCodes.NotFound)
          error(s"Received $status from storage service")
        Future.successful(None)
  }

  private def parseResponse(response: HttpResponse): Future[Option[Bag]] =
    Unmarshal(response.entity)
      .to[Bag]
      .map(Some(_))
      .recover { case exc =>
        error(s"Failed parsing response into bag", exc)
        None
      }
}
