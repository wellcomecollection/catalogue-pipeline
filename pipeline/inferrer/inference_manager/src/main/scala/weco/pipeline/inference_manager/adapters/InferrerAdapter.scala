package weco.pipeline.inference_manager.adapters

import org.apache.pekko.http.scaladsl.model.{
  HttpRequest,
  HttpResponse,
  StatusCodes
}
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.apache.pekko.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import grizzled.slf4j.Logging
import io.circe.Decoder
import weco.catalogue.internal_model.image.InferredData
import weco.pipeline.inference_manager.models.DownloadedImage

import java.nio.{ByteBuffer, ByteOrder}
import java.util.Base64
import scala.concurrent.Future
import scala.util.{Success, Try}

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

  def createRequest(image: DownloadedImage): HttpRequest
  def augment(
    inferredData: InferredData,
    inferrerResponse: Response
  ): InferredData

  def parseResponse(
    response: HttpResponse
  )(implicit mat: Materializer): Future[Response] =
    response.status match {
      case StatusCodes.OK =>
        Unmarshal(response.entity).to[Response]
      case StatusCodes.BadRequest =>
        Future.failed(new Exception("Bad request"))
      case StatusCodes.NotFound =>
        Future.failed(new Exception("Entity not found"))
      case statusCode =>
        Future.failed(
          new Exception(s"Request failed with code ${statusCode.value}")
        )
    }
}

object AdapterCommon {
  def decodeBase64ToFloatList(base64str: String): List[Float] = {
    // The JVM is big-endian whereas Python has encoded this with
    // little-endian ordering, so we need to manually set the order
    val buf = ByteBuffer
      .wrap(Base64.getDecoder.decode(base64str))
      .order(ByteOrder.LITTLE_ENDIAN)
    Stream
      .continually(Try(buf.getFloat))
      .takeWhile(_.isSuccess)
      .collect { case Success(f) => f }
      .toList
  }
}
