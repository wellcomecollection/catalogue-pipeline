package uk.ac.wellcome.platform.inference_manager.adapters

import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import grizzled.slf4j.Logging
import io.circe.Decoder

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
