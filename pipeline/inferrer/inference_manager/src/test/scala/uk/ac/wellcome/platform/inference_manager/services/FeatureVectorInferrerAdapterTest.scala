package uk.ac.wellcome.platform.inference_manager.services

import java.nio.file.Paths

import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inside, OptionValues}
import uk.ac.wellcome.models.work.generators.ImageGenerators
import uk.ac.wellcome.models.work.internal.{AugmentedImage, InferredData}
import uk.ac.wellcome.platform.inference_manager.fixtures.Encoding
import uk.ac.wellcome.platform.inference_manager.models.{
  DownloadedImage,
  FeatureVectorInferrerResponse
}

class FeatureVectorInferrerAdapterTest
    extends AnyFunSpec
    with Matchers
    with ImageGenerators
    with Inside
    with OptionValues {
  describe("createRequest") {
    it("creates a request with the image_url parameter as a local path") {
      val downloadedImage = DownloadedImage(
        image = createIdentifiedMergedImageWith(),
        path = Paths.get("/", "a", "b", "c.jpg")
      )
      val request = FeatureVectorInferrerAdapter.createRequest(downloadedImage)

      inside(request) {
        case HttpRequest(method, uri, _, _, _) =>
          method should be(HttpMethods.GET)
          uri.toString() should be(
            s"/feature-vector/?image_url=file://${downloadedImage.path}")
      }
    }
  }

  describe("augmentInput") {
    it("creates an AugmentedImage with the data from the inferrer response") {
      val downloadedImage = DownloadedImage(
        image = createIdentifiedMergedImageWith(),
        path = Paths.get("a", "b", "c.jpg")
      )
      val features = (0 until 4096).map(_ / 4096f).toList
      val featuresB64 = Encoding.toLittleEndianBase64(features)
      val lshEncodedFeatures = ('a' to 'z').map(_.toString * 3).toList
      val response = FeatureVectorInferrerResponse(
        features_b64 = featuresB64,
        lsh_encoded_features = lshEncodedFeatures
      )
      val augmentedImage =
        FeatureVectorInferrerAdapter.augmentInput(
          downloadedImage,
          Some(response))
      inside(augmentedImage) {
        case AugmentedImage(id, version, location, parentWork, inferredData) =>
          id should be(downloadedImage.image.id)
          version should be(downloadedImage.image.version)
          location should be(downloadedImage.image.location)
          parentWork should be(downloadedImage.image.source)
          inside(inferredData.value) {
            case InferredData(features1, features2, actualLshEncodedFeatures) =>
              features1 should be(features.slice(0, 2048))
              features2 should be(features.slice(2048, 4096))
              actualLshEncodedFeatures should be(lshEncodedFeatures)
          }
      }
    }
  }
}
