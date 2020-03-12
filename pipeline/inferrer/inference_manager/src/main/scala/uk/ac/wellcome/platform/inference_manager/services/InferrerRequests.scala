package uk.ac.wellcome.platform.inference_manager.services

import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{
  HttpMethods,
  HttpRequest,
  HttpResponse,
  StatusCodes,
  Uri
}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import uk.ac.wellcome.models.work.internal.MergedImage
import uk.ac.wellcome.platform.inference_manager.models.InferrerResponse

import scala.concurrent.Future

object InferrerRequests {
  def createRequest[Id](image: MergedImage[Id]): HttpRequest =
    HttpRequest(
      method = HttpMethods.GET,
      uri = Uri("/feature-vectors/").withQuery(
        Query(image.location.url match {
          case iiifUrl if iiifUrl.endsWith("json") => "iiif_url" -> iiifUrl
          case imageUrl                            => "image_url" -> imageUrl
        })
      )
    )

  def parseResponse(response: HttpResponse)(
    implicit materializer: Materializer): Future[InferrerResponse] =
    response.status match {
      case StatusCodes.OK =>
        Unmarshal(response.entity).to[InferrerResponse]
      case StatusCodes.BadRequest =>
        Future.failed(new Exception("Bad request"))
      case StatusCodes.NotFound =>
        Future.failed(new Exception("Image not found"))
      case statusCode =>
        Future.failed(
          new Exception(s"Request failed with code ${statusCode.value}"))
    }

  implicit val responseDecoder: Decoder[InferrerResponse] = deriveDecoder
}
