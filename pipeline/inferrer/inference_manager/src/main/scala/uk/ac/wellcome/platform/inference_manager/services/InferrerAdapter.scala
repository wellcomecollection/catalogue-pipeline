package uk.ac.wellcome.platform.inference_manager.services

import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import grizzled.slf4j.Logging
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import uk.ac.wellcome.models.work.internal.{AugmentedImage, Identified, InferredData, MergedImage}
import uk.ac.wellcome.platform.inference_manager.models.FeatureVectorInferrerResponse

import scala.concurrent.Future

/*
 * An InferrerAdapter is specific to the inferrer and the data that is being augmented.
 * Implementors must provide:
 * - The type of the inferrer response
 * - A Decoder for that response
 * - A function to create an HTTP request from the input data type
 * - A function to augment input data with a response, returning the output data type
 *
 * Additionally, the trait provides the logic for handling different HTTP response statuses
 */

trait InferrerAdapter[Input, Output] extends Logging {
  type InferrerResponse

  implicit val responseDecoder: Decoder[InferrerResponse]
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
}

// The InferrerAdaptor for feature vectors, consuming MergedImages and
// augmenting them into AugmentedImages
object FeatureVectorInferrerAdapter
    extends InferrerAdapter[MergedImage[Identified], AugmentedImage[Identified]] {
  type InferrerResponse = FeatureVectorInferrerResponse

  def createRequest(image: MergedImage[Identified]): HttpRequest =
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
    image: MergedImage[Identified],
    inferrerResponse: Option[InferrerResponse]): AugmentedImage[Identified] =
    image.augment {
      inferrerResponse collect {
        case FeatureVectorInferrerResponse(features, lsh_encoded_features)
            if features.size == 4096 =>
          val (features1, features2) = features.splitAt(features.size / 2)
          InferredData(
            features1 = features1,
            features2 = features2,
            lshEncodedFeatures = lsh_encoded_features
          )
      }
    }

  implicit val responseDecoder: Decoder[InferrerResponse] = deriveDecoder
}
