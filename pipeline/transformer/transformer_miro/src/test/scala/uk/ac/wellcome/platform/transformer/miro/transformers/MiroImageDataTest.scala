package uk.ac.wellcome.platform.transformer.miro.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.IdentifiersGenerators
import uk.ac.wellcome.models.work.internal.{
  DigitalLocationDeprecated,
  IdState,
  IdentifierType,
  ImageData,
  License,
  LocationType,
  SourceIdentifier
}
import uk.ac.wellcome.platform.transformer.miro.generators.MiroRecordGenerators

class MiroImageDataTest
    extends AnyFunSpec
    with Matchers
    with IdentifiersGenerators
    with MiroRecordGenerators {
  val transformer = new MiroImageData {}

  describe("getImageData") {
    it("extracts the Miro image data") {
      transformer.getImageData(
        createMiroRecordWith(
          imageNumber = "B0011308",
          useRestrictions = Some("CC-0"),
          sourceCode = Some("FDN")
        ),
        version = 1
      ) shouldBe ImageData[IdState.Identifiable](
        id = IdState.Identifiable(
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
