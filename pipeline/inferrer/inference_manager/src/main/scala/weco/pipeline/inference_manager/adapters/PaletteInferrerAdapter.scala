package weco.pipeline.inference_manager.adapters

import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import weco.catalogue.internal_model.image.InferredData
import weco.pipeline.inference_manager.models.{
  DownloadedImage,
  HashParams,
  PaletteInferrerResponse
}

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
      case PaletteInferrerResponse(palette, average_color_hex, params) =>
        inferredData.copy(
          palette = palette,
          averageColorHex = Some(average_color_hex),
          binSizes = params.bin_sizes,
          binMinima = params.bin_minima
        )
    }

  implicit val hashParamsDecoder: Decoder[HashParams] = deriveDecoder
  implicit val responseDecoder: Decoder[PaletteInferrerResponse] = deriveDecoder
}
