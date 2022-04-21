package weco.catalogue.display_model.image

import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.generators.ImageGenerators

class DisplayImageTest
    extends AnyFunSpec
    with Matchers
    with OptionValues
    with ImageGenerators {

  it("adds a list of visuallySimilar images if specified") {
    val image = createImageData.toIndexedImage
    val similarImages =
      (1 to 3).map(_ => createImageData.toIndexedImage).toSeq

    val displayImage = DisplayImage(
      image,
      includes = SingleImageIncludes.none,
      visuallySimilar = Some(similarImages),
      withSimilarColors = None,
      withSimilarFeatures = None
    )
    displayImage.visuallySimilar.value should have length similarImages.size
    displayImage.visuallySimilar.value
      .map(_.id) should contain theSameElementsAs
      similarImages.map(_.id)
  }

}
