package weco.pipeline.transformer.calm.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work._
import weco.catalogue.source_model.generators.CalmRecordGenerators
import weco.fixtures.RandomGenerators

class CalmFormatTest
    extends AnyFunSpec
    with Matchers
    with CalmRecordGenerators
    with RandomGenerators {
  it(
    "Correctly extracts the ArchivesDigital Format when the appropriate Material string is present"
  ) {
    val digitalRecord = createCalmRecordWith(
      ("Material", "Archives - Digital")
    )

    val digitalFormat = CalmFormat(digitalRecord)

    digitalFormat shouldBe Format.ArchivesDigital

    // Check that the label is "Archives - Digital"
    Format.ArchivesDigital.label shouldBe ("Archives - Digital")
  }

  it("Defaults to ArchivesAndManuscripts for all other contents of Material") {
    val nonDigitalRecords = (1 to 100)
      .map(_ => randomAlphanumeric())
      .map(
        materialName =>
          createCalmRecordWith(
            ("Material", materialName)
          )
      )

    val nonDigitalFormats = nonDigitalRecords.map(CalmFormat(_)).toSet

    nonDigitalFormats shouldBe Set(Format.ArchivesAndManuscripts)
  }
}
