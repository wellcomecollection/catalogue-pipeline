package weco.pipeline.mets_adapter.services

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.Uri.Path
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import grizzled.slf4j.Logging
import io.circe.generic.auto._
import weco.pipeline.mets_adapter.models._
import weco.http.client.HttpGet

import scala.concurrent.{ExecutionContext, Future}

trait BagRetriever {
  def getBag(space: String, externalIdentifier: String): Future[Bag]
}

class HttpBagRetriever(client: HttpGet)(
  implicit actorSystem: ActorSystem,
  executionContext: ExecutionContext
) extends BagRetriever
    with Logging {

  def getBag(space: String, externalIdentifier: String): Future[Bag] = {
    // Construct a URL to request a bag from the storage service.
    // See https://github.com/wellcomecollection/docs/tree/master/rfcs/002-archival_storage#bags
    val path = Path(s"$space/$externalIdentifier")

    debug(s"Making request to $path")
    for {
      response <- client.get(path)

      maybeBag <- {
        debug(s"Received response ${response.status}")
        handleResponse(space, externalIdentifier, response)
      }
    } yield maybeBag
  }

  private def handleResponse(
    space: String,
    externalIdentifier: String,
    response: HttpResponse
  ): Future[Bag] =
    response.status match {
      case StatusCodes.OK => parseResponseIntoBag(response)
      case StatusCodes.NotFound =>
        Future.failed(
          new Exception(
            s"Bag $space/$externalIdentifier does not exist in storage service"
          )
        )
      case StatusCodes.Unauthorized =>
        Future.failed(new Exception("Failed to authorize with storage service"))
      case status =>
        Future.failed(
          new Exception(s"Received error from storage service: $status")
        )
    }

  private def parseResponseIntoBag(response: HttpResponse): Future[Bag] =
    Unmarshal(response.entity).to[Bag].recover {
      case err =>
        throw new Exception(s"Failed parsing response into a Bag: $err")
    }
}
