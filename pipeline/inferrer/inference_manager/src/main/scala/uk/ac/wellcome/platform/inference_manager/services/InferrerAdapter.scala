package uk.ac.wellcome.platform.inference_manager.services

import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import uk.ac.wellcome.models.work.internal.MergedImage
import uk.ac.wellcome.platform.inference_manager.models.InferrerResponse

import scala.concurrent.Future

trait InferrerAdapter[Input, Output] {
  def createRequest(input: Input): HttpRequest
  def parseResponse(response: HttpResponse)(
    implicit mat: Materializer): Future[Output]
}

object FeatureVectorInferrerAdapter
    extends InferrerAdapter[MergedImage[_], InferrerResponse] {
  def createRequest(image: MergedImage[_]): HttpRequest =
    HttpRequest(
      method = HttpMethods.GET,
      uri = Uri("/feature-vectors/").withQuery(
        Uri.Query(image.location.url match {
          case iiifUrl if iiifUrl.endsWith("json") => "iiif_url" -> iiifUrl
          case imageUrl                            => "image_url" -> imageUrl
        })
      )
    )

  def parseResponse(response: HttpResponse)(
    implicit mat: Materializer): Future[InferrerResponse] =
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
