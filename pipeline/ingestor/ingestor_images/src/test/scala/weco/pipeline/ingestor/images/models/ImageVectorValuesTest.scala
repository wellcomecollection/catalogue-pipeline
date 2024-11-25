package weco.pipeline.ingestor.images.models

import org.scalatest.Inside
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class ImageVectorValuesTest
    extends AnyFunSpec
    with Matchers
    with ImageIngestorTestData
    with Inside {

  it("creates vector values from an image") {
    inside(ImageVectorValues(testImage)) {
      case ImageVectorValues(
            features,
            paletteEmbedding
          ) =>
        features shouldBe testImage.state.inferredData.features
        paletteEmbedding shouldBe testImage.state.inferredData.paletteEmbedding
    }
  }

}
