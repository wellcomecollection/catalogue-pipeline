package weco.pipeline.inference_manager.adapters

import org.apache.pekko.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import weco.catalogue.internal_model.image.InferredData
import weco.pipeline.inference_manager.models.{
  AspectRatioInferrerResponse,
  DownloadedImage
}

class AspectRatioInferrerAdapter(host: String, port: Int)
    extends InferrerAdapter {
  type Response = AspectRatioInferrerResponse

  def createRequest(image: DownloadedImage): HttpRequest =
    HttpRequest(
      method = HttpMethods.GET,
      uri = Uri("/aspect-ratio/")
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
      case AspectRatioInferrerResponse(aspect_ratio) =>
        inferredData.copy(aspectRatio = aspect_ratio)
    }

  implicit val responseDecoder: Decoder[Response] = deriveDecoder

}
