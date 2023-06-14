package weco.pipeline.inference_manager.adapters

import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import weco.catalogue.internal_model.image.InferredData
import weco.pipeline.inference_manager.models.{
  DownloadedImage,
  PaletteInferrerResponse
}
import AdapterCommon.decodeBase64ToFloatList

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
          averageColorHex = Some(average_color_hex)
        )
    }

  implicit val responseDecoder: Decoder[PaletteInferrerResponse] = deriveDecoder
}
