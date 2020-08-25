package uk.ac.wellcome.platform.inference_manager.adapters

import java.nio.file.Paths

import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inside, OptionValues}
import uk.ac.wellcome.models.work.generators.ImageGenerators
import uk.ac.wellcome.models.work.internal.InferredData
import uk.ac.wellcome.platform.inference_manager.models.{
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
        image = createIdentifiedMergedImageWith(),
        path = Paths.get("/", "a", "b", "c.jpg")
      )
      val request = adapter.createRequest(downloadedImage)

      inside(request) {
        case HttpRequest(method, uri, _, _, _) =>
          method should be(HttpMethods.GET)
          uri.toString() should be(
            s"http://palette_inferrer:80/palette/?query_url=file://${downloadedImage.path}")
      }
    }
  }

  describe("augment") {
    it("augments InferredData with the data from the inferrer response") {
      val palette = (0 to 100).map(n => f"$n%03d").toList
      val response = PaletteInferrerResponse(
        palette = palette
      )
      val inferredData = adapter.augment(InferredData.empty, response)
      inside(inferredData) {
        case InferredData(_, _, _, paletteResponse) =>
          paletteResponse should be(palette)
      }
    }
  }
}
