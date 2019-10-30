package uk.ac.wellcome.platform.transformer.mets.service

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import grizzled.slf4j.Logging
import io.circe.generic.auto._
import uk.ac.wellcome.platform.transformer.mets.model.Bag

import scala.concurrent.{ExecutionContext, Future}



class BagsRetriever(url: String, tokenService: TokenService)(implicit actorSystem: ActorSystem,
                                 materializer: ActorMaterializer,
                                 executionContext: ExecutionContext)
    extends Logging {
  def getBag(space: String, bagId: String): Future[Option[Bag]] = {
    debug(s"Executing request to $url/$space/$bagId")
    for {
      token <- tokenService.getToken
      response <- Http().singleRequest(HttpRequest(uri = s"$url/$space/$bagId").addHeader(Authorization(token)))
      maybeBag <- {
        debug(s"Received response ${response.status}")
        response.status match {
          case StatusCodes.OK => parseIntoBag(response)
          case StatusCodes.NotFound => Future.successful(None)
          case StatusCodes.Unauthorized => Future.failed(new Exception("Failed to authorize with storage service"))
          case _ =>
            Future.failed(new Exception("Received error from storage service"))
        }
      }
    } yield maybeBag  }

  private def parseIntoBag(response: HttpResponse) =
    Unmarshal(response.entity).to[Bag].map(Some(_)).recover {
    case t =>
      debug("Failed parsing response", t)
      throw new Exception("Failed parsing response into a Bag")
  }
}
