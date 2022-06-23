package weco.catalogue.display_model.image

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.display_model.locations.DisplayDigitalLocation
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.locations.LocationType

class DisplayImageTest extends AnyFunSpec with Matchers with ImageGenerators {
  it(
    "sets the thumbnail to the first iiif-image location it finds in locations") {
    val imageLocation = createDigitalLocationWith(
      locationType = LocationType.IIIFImageAPI
    )

    val locations = List(
      imageLocation,
      createManifestLocation,
      createDigitalLocationWith(
        locationType = LocationType.IIIFImageAPI
      )
    )

    val image = createImageDataWith(locations = locations).toIndexedImage
    val displayImage = DisplayImage(image)

    displayImage.thumbnail shouldBe DisplayDigitalLocation(imageLocation)
  }

  it("throws an error if there is no iiif-image location") {
    val image =
      createImageDataWith(locations = List(createManifestLocation)).toIndexedImage

    assertThrows[RuntimeException] {
      DisplayImage(image)
    }
  }
}
