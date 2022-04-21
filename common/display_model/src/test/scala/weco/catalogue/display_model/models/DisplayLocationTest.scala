package weco.catalogue.display_model.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.generators.LocationGenerators
import weco.catalogue.internal_model.locations._

class DisplayLocationTest
    extends AnyFunSpec
    with Matchers
    with LocationGenerators {

  // TODO: These tests could be rewritten to use generators

  describe("DisplayDigitalLocation") {
    it("reads a DigitalLocation as a DisplayDigitalLocation") {
      val thumbnailUrl = "https://iiif.example.org/V0000001/default.jpg"
      val locationType = LocationType.ThumbnailImage

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
        locationType
      )
      displayDigitalLocation.url shouldBe thumbnailUrl
      displayDigitalLocation.license shouldBe Some(
        DisplayLicense(internalLocation.license.get)
      )
      displayDigitalLocation.ontologyType shouldBe "DigitalLocation"
    }

    it("reads the credit field from a Location") {
      val location = DigitalLocation(
        locationType = LocationType.ThumbnailImage,
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
      val locationType = LocationType.ClosedStores
      val locationLabel = LocationType.ClosedStores.label
      val physicalLocation =
        PhysicalLocation(locationType = locationType, label = locationLabel)

      val displayLocation = DisplayLocation(physicalLocation)

      displayLocation shouldBe DisplayPhysicalLocation(
        locationType = DisplayLocationType(locationType),
        locationLabel
      )
    }

    it("copies the License from a PhysicalLocation") {
      val physicalLocation = PhysicalLocation(
        locationType = LocationType.ClosedStores,
        label = LocationType.ClosedStores.label,
        license = Some(License.CCBY)
      )

      val displayLocation = DisplayPhysicalLocation(physicalLocation)
      displayLocation.license shouldBe Some(DisplayLicense(License.CCBY))
    }

    it("copies the shelfmark from a PhysicalLocation") {
      val physicalLocation = PhysicalLocation(
        locationType = LocationType.ClosedStores,
        label = LocationType.ClosedStores.label,
        shelfmark = Some("PP/Shelved:Box 1")
      )

      val displayLocation = DisplayPhysicalLocation(physicalLocation)
      displayLocation.shelfmark shouldBe Some("PP/Shelved:Box 1")
    }
  }

  describe("DisplayDigitalLocation") {
    it("creates a DisplayDigitalLocation from a DigitalLocation") {
      val locationType = LocationType.IIIFPresentationAPI
      val url = "https://wellcomelibrary.org/iiif/b2201508/manifest"

      val digitalLocation =
        DigitalLocation(url = url, locationType = locationType)

      DisplayLocation(digitalLocation) shouldBe DisplayDigitalLocation(
        locationType = DisplayLocationType(locationType),
        url = url
      )
    }

    it("copies the license from a DigitalLocation") {
      val digitalLocation = DigitalLocation(
        locationType = LocationType.OnlineResource,
        url = "https://example.org/public-works/page.html",
        license = Some(License.PDM)
      )

      val displayLocation = DisplayDigitalLocation(digitalLocation)
      displayLocation.license shouldBe Some(DisplayLicense(License.PDM))
    }

    it("copies the link_text from a DigitalLocation") {
      val digitalLocation = DigitalLocation(
        locationType = LocationType.OnlineResource,
        url = "https://example.org/journals/browse.aspx",
        linkText = Some("View this journal")
      )

      val displayLocation = DisplayDigitalLocation(digitalLocation)
      displayLocation.linkText shouldBe Some("View this journal")
    }
  }
}
