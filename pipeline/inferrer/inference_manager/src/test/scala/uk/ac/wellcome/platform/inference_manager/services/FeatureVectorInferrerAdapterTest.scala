package uk.ac.wellcome.platform.inference_manager.services

import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inside, OptionValues}
import uk.ac.wellcome.models.work.generators.ImageGenerators
import uk.ac.wellcome.models.work.internal.{AugmentedImage, InferredData}
import uk.ac.wellcome.platform.inference_manager.fixtures.Encoding
import uk.ac.wellcome.platform.inference_manager.models.FeatureVectorInferrerResponse

class FeatureVectorInferrerAdapterTest
    extends AnyFunSpec
    with Matchers
    with ImageGenerators
    with Inside
    with OptionValues {
  describe("createRequest") {
    it("creates a request with the image_url parameter for image locations") {
      val imageUrl = "http://images.com/bananas/cavendish.jp2"
      val image = createMergedImageWith(
        location = createDigitalLocationWith(
          url = imageUrl
        )
      ).toIdentified
      val request = FeatureVectorInferrerAdapter.createRequest(image)

      inside(request) {
        case HttpRequest(method, uri, _, _, _) =>
          method should be(HttpMethods.GET)
          uri.toString() should be(s"/feature-vector/?image_url=${imageUrl}")
      }
    }

    it("creates a request with the iiif_url parameter for IIIF locations") {
      val imageUrl = "http://images.com/apples/braeburn/info.json"
      val image = createMergedImageWith(
        location = createDigitalLocationWith(
          url = imageUrl
        )
      ).toIdentified
      val request = FeatureVectorInferrerAdapter.createRequest(image)

      inside(request) {
        case HttpRequest(method, uri, _, _, _) =>
          method should be(HttpMethods.GET)
          uri.toString() should be(s"/feature-vector/?iiif_url=${imageUrl}")
      }
    }
  }

  describe("augmentInput") {
    it("creates an AugmentedImage with the data from the inferrer response") {
      val image = createMergedImage.toIdentified
      val features = (0 until 4096).map(_ / 4096f).toList
      val featuresB64 = Encoding.toLittleEndianBase64(features)
      val lshEncodedFeatures = ('a' to 'z').map(_.toString * 3).toList
      val response = FeatureVectorInferrerResponse(
        features_b64 = featuresB64,
        lsh_encoded_features = lshEncodedFeatures
      )
      val augmentedImage =
        FeatureVectorInferrerAdapter.augmentInput(image, Some(response))
      inside(augmentedImage) {
        case AugmentedImage(
            id,
            version,
            location,
            parentWork,
            fullText,
            inferredData) =>
          id should be(image.id)
          version should be(image.version)
          location should be(image.location)
          parentWork should be(image.source)
          fullText should be(image.fullText)
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
