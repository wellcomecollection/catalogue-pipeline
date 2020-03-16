package uk.ac.wellcome.platform.inference_manager.services

import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import grizzled.slf4j.Logging
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import uk.ac.wellcome.models.work.internal.{
  AugmentedImage,
  InferredData,
  MergedImage,
  Minted
}
import uk.ac.wellcome.platform.inference_manager.models.FeatureVectorInferrerResponse

import scala.concurrent.Future

trait InferrerAdapter[Input, Output] extends Logging {
  type InferrerResponse

  def createRequest(input: Input): HttpRequest
  def augmentInput(input: Input,
                   inferrerResponse: Option[InferrerResponse]): Output

  def parseResponse(response: HttpResponse)(
    implicit mat: Materializer): Future[Option[InferrerResponse]] =
    response.status match {
      case StatusCodes.OK =>
        Unmarshal(response.entity).to[Some[InferrerResponse]]
      case StatusCodes.BadRequest =>
        Future.failed(new Exception("Bad request"))
      case StatusCodes.NotFound =>
        Future.failed(new Exception("Entity not found"))
      case statusCode =>
        warn(
          s"Request failed non-deterministically with code ${statusCode.value}")
        Future.successful(None)
    }

  implicit val responseDecoder: Decoder[InferrerResponse]
}

object FeatureVectorInferrerAdapter
    extends InferrerAdapter[MergedImage[Minted], AugmentedImage[Minted]] {
  type InferrerResponse = FeatureVectorInferrerResponse

  def createRequest(image: MergedImage[Minted]): HttpRequest =
    HttpRequest(
      method = HttpMethods.GET,
      uri = Uri("/feature-vectors/").withQuery(
        Uri.Query(image.location.url match {
          case iiifUrl if iiifUrl.endsWith("json") => "iiif_url" -> iiifUrl
          case imageUrl                            => "image_url" -> imageUrl
        })
      )
    )

  def augmentInput(
    image: MergedImage[Minted],
    inferrerResponse: Option[InferrerResponse]): AugmentedImage[Minted] =
    inferrerResponse match {
      case Some(
          FeatureVectorInferrerResponse(features, lsh_encoded_features)) =>
        val (features1, features2) = features.splitAt(features.size / 2)
        image.augment {
          InferredData(
            features1 = features1,
            features2 = features2,
            lshEncodedFeatures = lsh_encoded_features
          )
        }
      case None => image.augmentWithNone
    }

  implicit val responseDecoder: Decoder[InferrerResponse] = deriveDecoder
}
