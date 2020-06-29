package uk.ac.wellcome.display.models

import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.ImageGenerators

class DisplayImageTest
    extends AnyFunSpec
    with Matchers
    with OptionValues
    with ImageGenerators {

  it("adds a list of visuallySimilar images if specified") {
    val image = createAugmentedImage()
    val similarImages = (1 to 3).map(_ => createAugmentedImage()).toSeq

    val displayImage = DisplayImage(image, similar = similarImages)
    displayImage.visuallySimilar.value should have length similarImages.size
    displayImage.visuallySimilar.value
      .map(_.id) should contain theSameElementsAs
      similarImages.map(_.id.canonicalId)
  }

}
