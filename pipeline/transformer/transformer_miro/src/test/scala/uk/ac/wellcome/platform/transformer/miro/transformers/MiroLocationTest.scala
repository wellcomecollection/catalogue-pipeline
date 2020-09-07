package uk.ac.wellcome.platform.transformer.miro.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal.{
  DigitalLocation,
  License,
  LocationType
}
import uk.ac.wellcome.platform.transformer.miro.generators.MiroRecordGenerators

class MiroLocationTest
    extends AnyFunSpec
    with Matchers
    with MiroRecordGenerators {
  val transformer = new MiroLocation {}
  it(
    "extracts the digital location and finds the credit line for an image-specific contributor code") {
    transformer.getLocation(
      createMiroRecordWith(
        sourceCode = Some("FDN"),
        useRestrictions = Some("CC-0"),
        imageNumber = "B0011308"
      )
    ) shouldBe DigitalLocation(
      "https://iiif.wellcomecollection.org/image/B0011308.jpg/info.json",
      LocationType("iiif-image"),
      Some(License.CC0),
      credit = Some("Ezra Feilden")
    )
  }
}
