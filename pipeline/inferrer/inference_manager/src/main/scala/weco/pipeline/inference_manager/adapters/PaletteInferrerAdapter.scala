package weco.pipeline.inference_manager.adapters

import java.nio.{ByteBuffer, ByteOrder}
import java.util.Base64

import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import weco.catalogue.internal_model.image.InferredData
import weco.pipeline.inference_manager.models.{
  DownloadedImage,
  PaletteInferrerResponse
}

import scala.util.{Success, Try}

class PaletteInferrerAdapter(host: String, port: Int) extends InferrerAdapter {
  type Response = PaletteInferrerResponse

  def createRequest(image: DownloadedImage): HttpRequest =
    HttpRequest(
      method = HttpMethods.GET,
      uri = Uri("/palette/")
        .withQuery(
          Uri.Query(
            "query_url" -> Uri
              .from(scheme = "file", path = image.pathString)
              .toString
          )
        )
        .withHost(host)
        .withPort(port)
        .withScheme("http")
    )

  def augment(
    inferredData: InferredData,
    inferrerResponse: Response
  ): InferredData =
    inferrerResponse match {
      case PaletteInferrerResponse(palette_embedding, average_color_hex) =>
        val paletteEmbedding = decodeBase64ToFloatList(palette_embedding)
        inferredData.copy(
          paletteEmbedding = paletteEmbedding,
          averageColorHex = Some(average_color_hex),
        )
    }

  private def decodeBase64ToFloatList(base64str: String): List[Float] = {
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

  implicit val responseDecoder: Decoder[PaletteInferrerResponse] = deriveDecoder
}
