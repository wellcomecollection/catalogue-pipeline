package weco.pipeline.transformer.mets.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.locations.{
  AccessStatus,
  DigitalLocation,
  License,
  LocationType
}
import weco.pipeline.transformer.mets.transformer.models.FileReference

class MetsThumbnailTest extends AnyFunSpec with Matchers {

  describe("Creating a DigitalLocation for a thumbnail") {
    it("returns a DigitalLocation using the location from the File Reference") {
      MetsThumbnail(
        Some(
          FileReference("id", "b123456789_HERE", Some("image/x-xwindowdump"))
        ),
        "b123456789",
        Some(License.PDM),
        Some(AccessStatus.Open)
      ).get shouldBe DigitalLocation(
        url =
          "https://iiif.wellcomecollection.org/thumbs/b123456789_HERE/full/!200,200/0/default.jpg",
        locationType = LocationType.ThumbnailImage,
        license = Some(License.PDM)
      )
    }

    it("uses the DLCS /thumbs/ prefix for images") {
      MetsThumbnail(
        Some(
          FileReference("id", "b123456789_HERE", Some("image/x-minolta-mrw"))
        ),
        "b123456789",
        Some(License.PDM),
        Some(AccessStatus.Open)
      ).get.url should include("/thumbs/b123456789_HERE/")
    }

    it(
      "uses the iiif-builder /thumb/ prefix with the document's id for non-images"
    ) {
      MetsThumbnail(
        Some(FileReference("id", "b123456789_HERE", Some("application/pdf"))),
        "b123456789",
        Some(License.PDM),
        Some(AccessStatus.Open)
      ).get.url should endWith("/thumb/b123456789")
    }
  }

  describe("href normalisation") {
    it("prepends the b number if absent") {
      MetsThumbnail(
        Some(FileReference("id", "HERE", Some("image/x-minolta-mrw"))),
        "b123456789",
        Some(License.PDM),
        Some(AccessStatus.Open)
      ).get.url should include("/thumbs/b123456789_HERE/")
    }

    it("removes a leading 'objects/' path part") {
      MetsThumbnail(
        Some(
          FileReference(
            "id",
            "objects/b123456789_HERE",
            Some("image/x-minolta-mrw")
          )
        ),
        "b123456789",
        Some(License.PDM),
        Some(AccessStatus.Open)
      ).get.url should include("/thumbs/b123456789_HERE/")
    }

    it("removes the objects prefix AND prepends the b number") {
      MetsThumbnail(
        Some(FileReference("id", "objects/HERE", Some("image/x-minolta-mrw"))),
        "b123456789",
        Some(License.PDM),
        Some(AccessStatus.Open)
      ).get.url should include("/thumbs/b123456789_HERE/")
    }
  }

  describe("Not creating a DigitalLocation") {
    it("returns None if there are access restrictions") {
      MetsThumbnail(
        Some(FileReference("id", "location", Some("image/xwd"))),
        "b123456789",
        Some(License.InCopyright),
        Some(AccessStatus.Restricted)
      ) shouldBe None
    }
    it("returns None if there is no file reference") {
      MetsThumbnail(
        None,
        "b123456789",
        Some(License.PDM),
        Some(AccessStatus.Open)
      ) shouldBe None
    }
  }

}
