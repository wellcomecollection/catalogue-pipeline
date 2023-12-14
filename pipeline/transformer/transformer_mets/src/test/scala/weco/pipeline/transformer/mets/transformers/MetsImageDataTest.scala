package weco.pipeline.transformer.mets.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.locations.LocationType.OnlineResource
import weco.catalogue.internal_model.locations.DigitalLocation
import weco.pipeline.transformer.mets.transformer.models.FileReference

class MetsImageDataTest extends AnyFunSpec with Matchers {
  describe("imageData URLs") {
    it("wraps the fileReference location with the rest of the URL") {
      imageDataWith(
        recordIdentifier = "b",
        fileReference = FileReference("a", "b_my_location")
      ).locations.head.url shouldBe "https://iiif.wellcomecollection.org/image/b_my_location/info.json"
    }

    it(
      "prepends the record identifier to the file reference location"
    ) {
      imageDataWith(
        recordIdentifier = "b30246039",
        fileReference = FileReference("a", "my_location")
      ).locations.head.url should include(
        "/image/b30246039_my_location/"
      )
    }

    describe("when the path starts with the record identifier") {
      it(
        "does not prepend another copy of the identifier"
      ) {
        imageDataWith(
          recordIdentifier = "b30246039",
          fileReference = FileReference("a", "b30246039_my_location")
        ).locations.head.url should include(
          "/image/b30246039_my_location/"
        )
      }

      it(
        "is case-insensitive in detecting the leading identifier"
      ) {
        imageDataWith(
          recordIdentifier = "b30246039",
          fileReference = FileReference("a", "B30246039_my_location")
        ).locations.head.url should include(
          "/image/B30246039_my_location/"
        )
      }
    }

    describe("when the path starts with objects/") {
      it(
        "strips leading objects/ path part before prepending the record identifier"
      ) {
        imageDataWith(
          recordIdentifier = "b30246039",
          fileReference = FileReference("a", "objects/my_location")
        ).locations.head.url should include(
          "/image/b30246039_my_location/"
        )
      }

      it(
        "does not insert an extra identifier if it is already there"
      ) {
        imageDataWith(
          recordIdentifier = "b30246039",
          fileReference = FileReference("a", "objects/b30246039_my_location")
        ).locations.head.url should include(
          "/image/b30246039_my_location/"
        )
      }
    }
  }

  private val nowhere = DigitalLocation(url = "", locationType = OnlineResource)

  private def imageDataWith(
    recordIdentifier: String,
    fileReference: FileReference
  ): ImageData[IdState.Identifiable] = {
    MetsImageData(
      recordIdentifier,
      1,
      None,
      nowhere,
      fileReference
    )
  }

}
