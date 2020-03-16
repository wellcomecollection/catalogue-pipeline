package uk.ac.wellcome.platform.inference_manager.services

import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import org.scalatest.{FunSpec, Inside, Matchers, OptionValues}
import uk.ac.wellcome.models.work.generators.ImageGenerators
import uk.ac.wellcome.models.work.internal.{AugmentedImage, InferredData}
import uk.ac.wellcome.platform.inference_manager.models.FeatureVectorInferrerResponse

class FeatureVectorInferrerAdapterTest
    extends FunSpec
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
      ).toMinted
      val request = FeatureVectorInferrerAdapter.createRequest(image)

      inside(request) {
        case HttpRequest(method, uri, _, _, _) =>
          method should be(HttpMethods.GET)
          uri.toString() should be(s"/feature-vectors/?image_url=${imageUrl}")
      }
    }

    it("creates a request with the iiif_url parameter for IIIF locations") {
      val imageUrl = "http://images.com/apples/braeburn/info.json"
      val image = createMergedImageWith(
        location = createDigitalLocationWith(
          url = imageUrl
        )
      ).toMinted
      val request = FeatureVectorInferrerAdapter.createRequest(image)

      inside(request) {
        case HttpRequest(method, uri, _, _, _) =>
          method should be(HttpMethods.GET)
          uri.toString() should be(s"/feature-vectors/?iiif_url=${imageUrl}")
      }
    }
  }

  describe("augmentInput") {
    it("creates an AugmentedImage with the data from the inferrer response") {
      val image = createMergedImage.toMinted
      val features = (0 until 4096).map(_ / 4096f).toList
      val lshEncodedFeatures = ('a' to 'z').map(_.toString * 3).toList
      val response = FeatureVectorInferrerResponse(
        features = features,
        lsh_encoded_features = lshEncodedFeatures
      )
      val augmentedImage =
        FeatureVectorInferrerAdapter.augmentInput(image, response)
      inside(augmentedImage) {
        case AugmentedImage(id, location, parentWork, fullText, inferredData) =>
          id should be(image.id)
          location should be(image.location)
          parentWork should be(image.parentWork)
          fullText should be(image.fullText)
          inside(inferredData.value) {
            case InferredData(features1, features2, lshEncodedFeatures) =>
              features1 should be(features.slice(0, 2048))
              features2 should be(features.slice(2048, 4096))
              lshEncodedFeatures should be(lshEncodedFeatures)
          }
      }
    }
  }
}
