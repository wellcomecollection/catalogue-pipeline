package uk.ac.wellcome.mets_adapter.services

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.unmarshalling.Unmarshal
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import grizzled.slf4j.Logging
import io.circe.generic.auto._
import uk.ac.wellcome.mets_adapter.models._

import scala.concurrent.{ExecutionContext, Future}

trait BagRetriever {
  def getBag(space: String, externalIdentifier: String): Future[Bag]
}

class HttpBagRetriever(baseUrl: String, tokenService: TokenService)(
  implicit
  actorSystem: ActorSystem,
  executionContext: ExecutionContext)
    extends BagRetriever
    with Logging {

  def getBag(space: String, externalIdentifier: String): Future[Bag] = {
    // Construct a URL to request a bag from the storage service.
    // See https://github.com/wellcomecollection/docs/tree/master/rfcs/002-archival_storage#bags
    val requestUri = Uri(s"$baseUrl/$space/$externalIdentifier")

    debug(s"Making request to $requestUri")
    for {
      token <- tokenService.getToken

      httpRequest = HttpRequest(uri = requestUri)
        .addHeader(Authorization(token))

      response <- Http().singleRequest(httpRequest)
      maybeBag <- {
        debug(s"Received response ${response.status}")
        handleResponse(response)
      }
    } yield maybeBag
  }

  private def handleResponse(response: HttpResponse): Future[Bag] =
    response.status match {
      case StatusCodes.OK => parseResponseIntoBag(response)
      case StatusCodes.NotFound =>
        Future.failed(new Exception("Bag does not exist on storage service"))
      case StatusCodes.Unauthorized =>
        Future.failed(new Exception("Failed to authorize with storage service"))
      case status =>
        Future.failed(
          new Exception(s"Received error from storage service: $status"))
    }

  private def parseResponseIntoBag(response: HttpResponse) =
    Unmarshal(response.entity).to[Bag].recover {
      case t =>
        throw new Exception("Failed parsing response into a Bag")
    }
}
