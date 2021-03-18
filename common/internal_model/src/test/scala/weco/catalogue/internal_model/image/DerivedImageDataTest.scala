package weco.catalogue.internal_model.image

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.work.{Contributor, Organisation, Person}

class DerivedImageDataTest extends AnyFunSpec with Matchers with ImageGenerators {
  it(
    "sets the thumbnail to the first iiif-image location it finds in locations") {
    val imageLocation = createImageLocation
    val image = createImageDataWith(locations =
      List(createManifestLocation, imageLocation)).toAugmentedImage
    val derivedImageData = DerivedImageData(image)

    derivedImageData.thumbnail shouldBe imageLocation
  }

  it("throws an error if there is no iiif-image location") {
    val image =
      createImageDataWith(locations = List(createManifestLocation)).toAugmentedImage

    assertThrows[RuntimeException] {
      DerivedImageData(image)
    }
  }

  it(
    "constructs a list of contributor agent labels from the canonical source work") {
    val image = createImageData.toAugmentedImageWith(
      parentWork = identifiedWork().contributors(
        List(Contributor(Organisation("Planet Express"), roles = Nil))
      ),
      redirectedWork = Some(
        identifiedWork().contributors(
          List(Contributor(Person("Zaphod Beeblebrox"), roles = Nil))
        )
      )
    )
    val derivedImageData = DerivedImageData(image)

    derivedImageData.sourceContributorAgents should contain(
      "Organisation:Planet Express")
    derivedImageData.sourceContributorAgents should not contain "Person:Zaphod Beeblebrox"
  }
}
