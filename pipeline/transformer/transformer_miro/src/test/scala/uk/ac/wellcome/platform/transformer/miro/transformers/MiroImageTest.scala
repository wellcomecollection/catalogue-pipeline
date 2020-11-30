package uk.ac.wellcome.platform.transformer.miro.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.models.work.internal.{
  DigitalLocationDeprecated,
  IdentifierType,
  Image,
  ImageState,
  License,
  LocationType,
  SourceIdentifier,
}
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
          imageNumber = "B0011308",
          useRestrictions = Some("CC-0"),
          sourceCode = Some("FDN")
        ),
        version = 1
      ) shouldBe Image[ImageState.Source](
        state = ImageState.Source(
          sourceIdentifier = SourceIdentifier(
            identifierType = IdentifierType("miro-image-number"),
            ontologyType = "Image",
            value = "B0011308"
          )
        ),
        version = 1,
        locations = List(
          DigitalLocationDeprecated(
            url =
              "https://iiif.wellcomecollection.org/image/B0011308.jpg/info.json",
            locationType = LocationType("iiif-image"),
            license = Some(License.CC0),
            credit = Some("Ezra Feilden")
          ))
      )
    }
  }
}
