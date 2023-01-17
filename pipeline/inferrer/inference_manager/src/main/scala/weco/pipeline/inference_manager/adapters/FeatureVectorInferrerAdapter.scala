package weco.pipeline.inference_manager.adapters

import java.nio.{ByteBuffer, ByteOrder}
import java.util.Base64
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, Uri}
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import weco.catalogue.internal_model.image.InferredData
import weco.pipeline.inference_manager.models.{
  DownloadedImage,
  FeatureVectorInferrerResponse
}

import scala.util.{Success, Try}

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
          features_b64,
          reduced_features_b64
          ) =>
        val features = decodeBase64ToFloatList(features_b64)
        val reducedFeatures = decodeBase64ToFloatList(
          reduced_features_b64
        )
        if (features.size == 4096) {
          val (features1, features2) = features.splitAt(features.size / 2)
          inferredData.copy(
            features1 = features1,
            features2 = features2,
            reducedFeatures = reducedFeatures
          )
        } else inferredData
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

  implicit val responseDecoder: Decoder[Response] = deriveDecoder
}
