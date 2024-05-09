package weco.pipeline.transformer.miro.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.locations._
import weco.catalogue.source_model.miro.MiroSourceOverrides
import weco.pipeline.transformer.miro.generators.MiroRecordGenerators

class MiroImageDataTest
    extends AnyFunSpec
    with Matchers
    with IdentifiersGenerators
    with MiroRecordGenerators {
  val transformer = new MiroImageData {}

  describe("getImageData") {
    it("extracts the Miro image data") {
      val imageData = transformer.getImageData(
        miroRecord = createMiroRecordWith(
          imageNumber = "B0011308",
          useRestrictions = Some("CC-0"),
          sourceCode = Some("FDN")
        ),
        overrides = MiroSourceOverrides.empty,
        version = 1
      )

      imageData shouldBe ImageData[IdState.Identifiable](
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
            url =
              "https://iiif.wellcomecollection.org/image/B0011308/info.json",
            locationType = LocationType.IIIFImageAPI,
            license = Some(License.CC0),
            credit = Some("Ezra Feilden"),
            accessConditions = List(
              AccessCondition(
                method = AccessMethod.ViewOnline,
                status = AccessStatus.Open
              )
            )
          )
        )
      )
    }

    it("uses the source overrides") {
      val miroRecord = createMiroRecordWith(useRestrictions = Some("CC-0"))

      val imageData1 = transformer.getImageData(
        miroRecord = miroRecord,
        overrides = MiroSourceOverrides.empty,
        version = 1
      )

      imageData1.locations.head.license shouldBe Some(License.CC0)

      val imageData2 = transformer.getImageData(
        miroRecord = miroRecord,
        overrides = MiroSourceOverrides(
          license = Some(License.InCopyright)
        ),
        version = 1
      )

      imageData2.locations.head.license shouldBe Some(License.InCopyright)
    }
  }
}
