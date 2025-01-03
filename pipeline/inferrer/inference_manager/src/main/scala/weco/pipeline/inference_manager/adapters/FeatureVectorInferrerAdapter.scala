package weco.pipeline.inference_manager.adapters

import org.apache.pekko.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import weco.catalogue.internal_model.image.InferredData
import weco.pipeline.inference_manager.models.{
  DownloadedImage,
  FeatureVectorInferrerResponse
}
import AdapterCommon.decodeBase64ToFloatList

// The InferrerAdaptor for feature vectors, consuming Image[Identified] and
// augmenting them into Image[Augmented]
class FeatureVectorInferrerAdapter(val host: String, port: Int)
    extends InferrerAdapter {
  type Response = FeatureVectorInferrerResponse

  def createRequest(image: DownloadedImage): HttpRequest =
    HttpRequest(
      method = HttpMethods.GET,
      uri = Uri("/feature-vector/")
        .withQuery(
          Uri.Query(
            "query_url" -> Uri
              .from(
                scheme = "file",
                path = image.pathString
              )
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
      case FeatureVectorInferrerResponse(
            features_b64
          ) =>
        val features = decodeBase64ToFloatList(features_b64)

        if (features.size == 4096) {
          inferredData.copy(
            features = features
          )
        } else inferredData
    }

  implicit val responseDecoder: Decoder[Response] = deriveDecoder
}
