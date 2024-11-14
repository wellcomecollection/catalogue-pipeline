package weco.pipeline.inference_manager.adapters

import java.nio.file.Paths
import org.apache.pekko.http.scaladsl.model.{HttpMethods, HttpRequest}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inside, OptionValues}
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.image.InferredData
import weco.pipeline.inference_manager.fixtures.Encoding
import weco.pipeline.inference_manager.models.{
  DownloadedImage,
  FeatureVectorInferrerResponse
}

class FeatureVectorInferrerAdapterTest
    extends AnyFunSpec
    with Matchers
    with ImageGenerators
    with Inside
    with OptionValues {
  val adapter = new FeatureVectorInferrerAdapter("feature_inferrer", 80)

  describe("createRequest") {
    it("creates a request with the query_url parameter as a local path") {
      val downloadedImage = DownloadedImage(
        image = createImageData.toInitialImage,
        path = Paths.get("/", "a", "b", "c.jpg")
      )
      val request = adapter.createRequest(downloadedImage)

      inside(request) {
        case HttpRequest(method, uri, _, _, _) =>
          method should be(HttpMethods.GET)
          uri.toString() should be(
            s"http://feature_inferrer:80/feature-vector/?query_url=file://${downloadedImage.path}"
          )
      }
    }
  }
}
