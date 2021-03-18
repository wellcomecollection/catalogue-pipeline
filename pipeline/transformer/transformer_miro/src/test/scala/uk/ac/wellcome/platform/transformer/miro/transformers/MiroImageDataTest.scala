package uk.ac.wellcome.platform.transformer.miro.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.platform.transformer.miro.generators.MiroRecordGenerators
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.locations._

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
            identifierType = IdentifierType.MiroImageNumber,
            ontologyType = "Image",
            value = "B0011308"
          )
        ),
        version = 1,
        locations = List(
          DigitalLocation(
            url = "https://iiif.wellcomecollection.org/image/B0011308/info.json",
            locationType = LocationType.IIIFImageAPI,
            license = Some(License.CC0),
            credit = Some("Ezra Feilden"),
            accessConditions = List(
              AccessCondition(status = Some(AccessStatus.Open))
            )
          ))
      )
    }
  }
}
