package weco.pipeline.inference_manager.adapters

import java.nio.file.Paths
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inside, OptionValues}
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.image.InferredData
import weco.pipeline.inference_manager.models.{
  DownloadedImage,
  HashParams,
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
      val palette = (0 to 25).map(n => f"$n%03d").toList
      val averageColorHex = "#aabbcc"
      val binSizes = List(List(1, 2, 3), List(4, 5, 6), List(7, 8, 9))
      val binMinima = List(0.1f, 0.2f, 0.3f)
      val response = PaletteInferrerResponse(
        palette = palette,
        average_color_hex = averageColorHex,
        hash_params = HashParams(
          bin_sizes = binSizes,
          bin_minima = binMinima
        )
      )
      val inferredData = adapter.augment(InferredData.empty, response)

      inferredData.palette should be(palette)
      inferredData.averageColorHex should be(averageColorHex)
      inferredData.binSizes should be(binSizes)
      inferredData.binMinima should be(binMinima)
    }
  }
}
