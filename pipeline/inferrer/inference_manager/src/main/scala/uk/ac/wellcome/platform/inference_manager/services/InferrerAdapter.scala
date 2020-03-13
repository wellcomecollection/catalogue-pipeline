package uk.ac.wellcome.platform.inference_manager.services

import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import uk.ac.wellcome.models.work.internal.{
  AugmentedImage,
  InferredData,
  MergedImage
}
import uk.ac.wellcome.platform.inference_manager.models.FeatureVectorInferrerResponse

import scala.concurrent.Future

trait InferrerAdapter[Input, Output] {
  type InferrerResponse

  def createRequest(input: Input): HttpRequest
  def augmentInput(input: Input, inferrerResponse: InferrerResponse): Output

  def parseResponse(response: HttpResponse)(
    implicit mat: Materializer): Future[InferrerResponse] =
    response.status match {
      case StatusCodes.OK =>
        Unmarshal(response.entity).to[InferrerResponse]
      case StatusCodes.BadRequest =>
        Future.failed(new Exception("Bad request"))
      case StatusCodes.NotFound =>
        Future.failed(new Exception("Entity not found"))
      case statusCode =>
        Future.failed(
          new Exception(s"Request failed with code ${statusCode.value}"))
    }

  implicit val responseDecoder: Decoder[InferrerResponse] = deriveDecoder
}

object FeatureVectorInferrerAdapter
    extends InferrerAdapter[MergedImage[_], AugmentedImage[_]] {
  type InferrerResponse = FeatureVectorInferrerResponse

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

  def augmentInput(image: MergedImage[_],
                   inferrerResponse: InferrerResponse): AugmentedImage[_] =
    inferrerResponse match {
      case FeatureVectorInferrerResponse(features, lsh_encoded_features) =>
        val (features1, features2) = features.splitAt(features.size / 2)
        image.augment {
          InferredData(
            features1 = features1,
            features2 = features2,
            lshEncodedFeatures = lsh_encoded_features
          )
        }
    }
}
