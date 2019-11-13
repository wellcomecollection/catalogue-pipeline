package uk.ac.wellcome.mets.services

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import grizzled.slf4j.Logging
import io.circe.generic.auto._
import uk.ac.wellcome.mets.models.Bag

import scala.concurrent.{ExecutionContext, Future}

class BagRetriever(url: String, tokenService: TokenService)(
  implicit
  actorSystem: ActorSystem,
  materializer: ActorMaterializer,
  executionContext: ExecutionContext)
    extends Logging {

  def getBag(update: StorageUpdate): Future[Option[Bag]] = {
    debug(s"Executing request to $url/${update.space}/${update.bagId}")
    for {
      token <- tokenService.getToken
      response <- Http().singleRequest(generateRequest(update, token))
      maybeBag <- {
        debug(s"Received response ${response.status}")
        handleResponse(response)
      }
    } yield maybeBag
  }

  private def generateRequest(update: StorageUpdate, token: OAuth2BearerToken): HttpRequest =
   HttpRequest(uri = s"$url/${update.space}/${update.bagId}")
     .addHeader(Authorization(token))

  private def handleResponse(response: HttpResponse): Future[Option[Bag]] =
    response.status match {
      case StatusCodes.OK       => parseResponseIntoBag(response)
      case StatusCodes.NotFound => Future.successful(None)
      case StatusCodes.Unauthorized =>
        Future.failed(
          new Exception("Failed to authorize with storage service"))
      case _ =>
        Future.failed(new Exception("Received error from storage service"))
    }

  private def parseResponseIntoBag(response: HttpResponse) =
    Unmarshal(response.entity).to[Bag].map(Some(_)).recover {
      case t =>
        throw new Exception("Failed parsing response into a Bag")
    }
}
