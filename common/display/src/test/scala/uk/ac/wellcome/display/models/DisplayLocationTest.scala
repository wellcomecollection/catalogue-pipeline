package uk.ac.wellcome.display.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal.{
  DigitalLocation,
  License,
  LocationType,
  PhysicalLocation
}

class DisplayLocationTest extends AnyFunSpec with Matchers {

  describe("DisplayDigitalLocation") {
    it("reads a DigitalLocation as a DisplayDigitalLocation") {
      val thumbnailUrl = "https://iiif.example.org/V0000001/default.jpg"
      val locationType = LocationType("thumbnail-image")

      val internalLocation = DigitalLocation(
        locationType = locationType,
        url = thumbnailUrl,
        license = Some(License.CCBY)
      )
      val displayLocation = DisplayLocation(internalLocation)

      displayLocation shouldBe a[DisplayDigitalLocation]
      val displayDigitalLocation =
        displayLocation.asInstanceOf[DisplayDigitalLocation]
      displayDigitalLocation.locationType shouldBe DisplayLocationType(
        locationType)
      displayDigitalLocation.url shouldBe thumbnailUrl
      displayDigitalLocation.license shouldBe Some(
        DisplayLicense(internalLocation.license.get))
      displayDigitalLocation.ontologyType shouldBe "DigitalLocation"
    }

    it("reads the credit field from a Location") {
      val location = DigitalLocation(
        locationType = LocationType("thumbnail-image"),
        url = "",
        credit = Some("Science Museum, Wellcome"),
        license = Some(License.CCBY)
      )
      val displayLocation = DisplayLocation(location)

      displayLocation shouldBe a[DisplayDigitalLocation]
      val displayDigitalLocation =
        displayLocation.asInstanceOf[DisplayDigitalLocation]
      displayDigitalLocation.credit shouldBe location.credit
    }
  }

  describe("DisplayPhysicalLocation") {
    it("creates a DisplayPhysicalLocation from a PhysicalLocation") {
      val locationType = LocationType("sgmed")
      val locationLabel = "The collection of cold cauldrons"
      val physicalLocation =
        PhysicalLocation(locationType = locationType, label = locationLabel)

      val displayLocation = DisplayLocation(physicalLocation)

      displayLocation shouldBe DisplayPhysicalLocation(
        locationType = DisplayLocationType(locationType),
        locationLabel)
    }
  }

  describe("DisplayDigitalLocation") {
    it("creates a DisplayDigitalLocation from a DigitalLocation") {
      val locationType = LocationType("iiif-image")
      val url = "https://wellcomelibrary.org/iiif/b2201508/manifest"

      val digitalLocation = DigitalLocation(url, locationType)

      DisplayLocation(digitalLocation) shouldBe DisplayDigitalLocation(
        locationType = DisplayLocationType(locationType),
        url = url)
    }
  }
}
