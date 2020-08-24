package uk.ac.wellcome.platform.inference_manager.adapters

import akka.http.scaladsl.model.Uri.Authority
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import grizzled.slf4j.Logging
import io.circe.Decoder
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.models.work.internal.{AugmentedImage, InferredData}
import uk.ac.wellcome.platform.inference_manager.models.DownloadedImage
import uk.ac.wellcome.platform.inference_manager.services.RequestPoolFlow

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
trait InferrerResponse

trait InferrerAdapter extends Logging {
  type Response <: InferrerResponse

  implicit val responseDecoder: Decoder[Response]

  val hostAuthority: Authority
  def createRequest(image: DownloadedImage): HttpRequest
  def augment(inferredData: InferredData,
              inferrerResponse: Response): InferredData

  def parseResponse(response: HttpResponse)(
    implicit mat: Materializer): Future[Response] =
    response.status match {
      case StatusCodes.OK =>
        Unmarshal(response.entity).to[Response]
      case StatusCodes.BadRequest =>
        Future.failed(new Exception("Bad request"))
      case StatusCodes.NotFound =>
        Future.failed(new Exception("Entity not found"))
      case statusCode =>
        Future.failed(
          new Exception(s"Request failed with code ${statusCode.value}"))
    }
}
