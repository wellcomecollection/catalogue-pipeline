package weco.pipeline.inference_manager.adapters

import java.nio.file.Paths
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inside, OptionValues}
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.image.InferredData
import weco.pipeline.inference_manager.fixtures.Encoding
import weco.pipeline.inference_manager.models.{
  DownloadedImage,
  PaletteInferrerResponse
}

class PaletteInferrerAdapterTest
    extends AnyFunSpec
    with Matchers
    with ImageGenerators
    with Inside
    with OptionValues {
  val adapter = new PaletteInferrerAdapter("palette_inferrer", 80)

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
            s"http://palette_inferrer:80/palette/?query_url=file://${downloadedImage.path}"
          )
      }
    }
  }

  describe("augment") {
    it("augments InferredData with the data from the inferrer response") {
      val paletteEmbedding = (0 to 216).map(n => n / 216.0f).toList // the sum of the elements in the List must be 1
      val paletteEmbeddingB64 =  Encoding.toLittleEndianBase64(paletteEmbedding)
      val averageColorHex = "#aabbcc"
      val response = PaletteInferrerResponse(
        palette_embedding = paletteEmbeddingB64,
        average_color_hex = averageColorHex
      )
      val inferredData = adapter.augment(InferredData.empty, response)
      println("!!!!!!!!!!!")
      println(inferredData.paletteEmbedding.length)
      inferredData.paletteEmbedding should be(paletteEmbedding)
      inferredData.averageColorHex.get should be(averageColorHex)
    }
  }
}
