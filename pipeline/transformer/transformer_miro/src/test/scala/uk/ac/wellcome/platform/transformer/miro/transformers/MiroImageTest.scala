package uk.ac.wellcome.platform.transformer.miro.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.models.work.internal.{DigitalLocation, IdentifierType, LocationType, SourceIdentifier, UnmergedImage}
import uk.ac.wellcome.platform.transformer.miro.generators.MiroRecordGenerators

class MiroImageTest
    extends AnyFunSpec
    with Matchers
    with IdentifiersGenerators
    with MiroRecordGenerators {
  val transformer = new MiroImage {}

  describe("getImage") {
    it("extracts the Miro image") {
      transformer.getImage(
        createMiroRecordWith(
          imageNumber = "B0011308"
        ),
        version = 1
      ) shouldBe UnmergedImage(
        sourceIdentifier = SourceIdentifier(
          identifierType = IdentifierType("miro-image-number"),
          ontologyType = "Image",
          value = "B0011308"
        ),
        version = 1,
        location = DigitalLocation(
          url =
            "https://iiif.wellcomecollection.org/image/B0011308.jpg/info.json",
          locationType = LocationType("iiif-image")
        )
      )
    }
  }
}
