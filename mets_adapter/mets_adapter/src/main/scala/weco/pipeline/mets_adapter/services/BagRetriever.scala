package weco.pipeline.mets_adapter.services

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model.Uri.Path
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.Location
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport._
import grizzled.slf4j.Logging
import io.circe.generic.auto._
import weco.pipeline.mets_adapter.models._
import weco.http.client.{HttpClient, HttpGet}

import scala.concurrent.{ExecutionContext, Future}

trait BagRetriever {
  def getBag(space: String, externalIdentifier: String): Future[Bag]
}

class HttpBagRetriever(client: HttpGet, redirectClient: HttpClient)(
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
      case StatusCodes.TemporaryRedirect =>
        response.discardEntityBytes()
        response.header[Location] match {
          case Some(location) =>
            val uri = location.uri
            // strip the query parameters from the URI before logging, contain credentials
            debug(s"Following redirect to ${uri.withQuery(Uri.Query.Empty)}")
            followRedirect(uri)
          case None =>
            Future.failed(
              new Exception(
                "Received 307 redirect from storage service but no Location header"
              )
            )
        }
      case StatusCodes.NotFound =>
        response.discardEntityBytes()
        Future.failed(
          new Exception(
            s"Bag $space/$externalIdentifier does not exist in storage service"
          )
        )
      case StatusCodes.Unauthorized =>
        response.discardEntityBytes()
        Future.failed(new Exception("Failed to authorize with storage service"))
      case status =>
        response.discardEntityBytes()
        Future.failed(
          new Exception(s"Received error from storage service: $status")
        )
    }

  private val allowedRedirectPrefix =
    "https://wellcomecollection-storage-prod-large-response-cache.s3.eu-west-1.amazonaws.com/responses/"

  private def followRedirect(uri: Uri): Future[Bag] =
    if (!uri.toString.startsWith(allowedRedirectPrefix))
      Future.failed(
        new Exception(
          s"Refusing to follow redirect to unexpected URL: ${uri.withQuery(Uri.Query.Empty)}"
        )
      )
    else {
      info(
        s"Following redirect for large bag response to ${uri.withQuery(Uri.Query.Empty)}"
      )
      for {
        response <- redirectClient.singleRequest(HttpRequest(uri = uri))
        bag <- response.status match {
          case StatusCodes.OK => parseRedirectedResponseIntoBag(uri, response)
          case status =>
            response.discardEntityBytes()
            // strip the query parameters from the URI before throwing, contain credentials
            Future.failed(
              new Exception(
                s"Received error following redirect to ${uri
                    .withQuery(Uri.Query.Empty)}: $status"
              )
            )
        }
      } yield bag
    }

  // The S3 large-response cache serves the cached body with
  // `Content-Type: application/octet-stream` even though the body is JSON.
  // Match on the entity's content type and normalise it before unmarshalling
  // so the circe unmarshaller (which only accepts application/json) will
  // accept it. Anything we don't recognise fails loudly.
  private def parseRedirectedResponseIntoBag(
    uri: Uri,
    response: HttpResponse
  ): Future[Bag] =
    response.entity.contentType match {
      case ContentTypes.`application/json` =>
        parseResponseIntoBag(response)
      case ct if ct.mediaType == MediaTypes.`application/octet-stream` =>
        info(
          s"Redirected bag response from ${uri.withQuery(Uri.Query.Empty)} had Content-Type $ct; treating as application/json"
        )
        parseResponseIntoBag(
          response.withEntity(
            response.entity.withContentType(ContentTypes.`application/json`)
          )
        )
      case ct =>
        response.discardEntityBytes()
        Future.failed(
          new Exception(
            s"Unexpected Content-Type from redirected bag response: $ct"
          )
        )
    }

  private def parseResponseIntoBag(response: HttpResponse): Future[Bag] =
    Unmarshal(response.entity).to[Bag].recover {
      case err =>
        throw new Exception(s"Failed parsing response into a Bag: $err")
    }
}
