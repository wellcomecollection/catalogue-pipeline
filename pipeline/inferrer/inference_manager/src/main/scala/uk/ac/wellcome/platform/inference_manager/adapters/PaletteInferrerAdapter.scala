package uk.ac.wellcome.platform.inference_manager.adapters

import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import uk.ac.wellcome.models.work.internal.InferredData
import uk.ac.wellcome.platform.inference_manager.models.{
  DownloadedImage,
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
            "image_url" -> Uri
              .from(scheme = "file", path = image.pathString)
              .toString
          ))
        .withHost(host)
        .withPort(port)
        .withScheme("http")
    )

  def augment(inferredData: InferredData,
              inferrerResponse: Response): InferredData =
    inferrerResponse match {
      case PaletteInferrerResponse(palette) =>
        inferredData.copy(palette = palette)
    }

  implicit val responseDecoder: Decoder[PaletteInferrerResponse] = deriveDecoder
}
