package uk.ac.wellcome.platform.transformer.mets.service


import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import grizzled.slf4j.Logging
import io.circe.generic.auto._
import uk.ac.wellcome.platform.transformer.mets.model.Bag

import scala.concurrent.{ExecutionContext, Future}

class BagsRetriever(url: String)(implicit actorSystem: ActorSystem, materializer: ActorMaterializer, executionContext: ExecutionContext) extends Logging {
  def getBag(space: String, bagId: String): Future[Option[Bag]] = {
    debug(s"Executing request to $url/$space/$bagId")
    for {
      response <- Http().singleRequest(HttpRequest(uri = s"$url/$space/$bagId"))
      maybeBag <- responseToBag(response)
    } yield maybeBag
  }

  private def responseToBag(response: HttpResponse) = {
    debug(s"Received response ${response.status}")
    response.status match {
      case StatusCodes.OK => Unmarshal(response.entity).to[Bag].map(Some(_)).recover{ case t =>
        debug("Failed parsing response", t)
        throw new Exception("Failed parsing response into a Bag")}
      case StatusCodes.NotFound => Future.successful(None)
      case _ =>
        Future.failed(new Exception("Received error from storage service"))
    }
  }
}
