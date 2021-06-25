package uk.ac.wellcome.platform.transformer.miro.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.platform.transformer.miro.generators.MiroRecordGenerators
import weco.catalogue.internal_model.locations._
import weco.catalogue.source_model.miro.MiroSourceOverrides

class MiroLocationTest
    extends AnyFunSpec
    with Matchers
    with MiroRecordGenerators {
  val transformer = new MiroLocation {}
  it(
    "extracts the digital location and finds the credit line for an image-specific contributor code") {
    val location = transformer.getLocation(
      miroRecord = createMiroRecordWith(
        sourceCode = Some("FDN"),
        useRestrictions = Some("CC-0"),
        imageNumber = "B0011308"
      ),
      overrides = MiroSourceOverrides.empty
    )

    location shouldBe DigitalLocation(
      url = "https://iiif.wellcomecollection.org/image/B0011308/info.json",
      locationType = LocationType.IIIFImageAPI,
      license = Some(License.CC0),
      credit = Some("Ezra Feilden"),
      accessConditions = List(
        AccessCondition(
          method = AccessMethod.ViewOnline,
          status = AccessStatus.Open)
      )
    )
  }
}
