package uk.ac.wellcome.platform.transformer.mets.service


import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import grizzled.slf4j.Logging
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.transformer.mets.model.Bag

import scala.concurrent.{ExecutionContext, Future}

class BagsRetriever(url: String)(implicit actorSystem: ActorSystem, materializer: ActorMaterializer, executionContext: ExecutionContext) extends Logging {
  def getBag(space: String, bagId: String): Future[Option[Bag]] = {
    debug(s"Executing request to $url/$space/$bagId")
    for {
      response <- Http().singleRequest(HttpRequest(uri = s"$url/$space/$bagId"))
      responseAsString <- Unmarshal(response.entity).to[String]
      maybeBag <- jhgj(response, responseAsString)
    } yield maybeBag
  }

  private def jhgj(response: HttpResponse, responseAsString: String) = {
    response.status match {
      case StatusCodes.OK => Future.successful(Some(fromJson[Bag](responseAsString).get))
      case StatusCodes.NotFound => Future.successful(None)
      case _ => Future.failed(new Exception("Received error from storage service"))
    }
  }
}
