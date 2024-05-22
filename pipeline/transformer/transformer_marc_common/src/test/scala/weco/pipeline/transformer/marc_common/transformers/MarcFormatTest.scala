package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.OptionValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.Format.{EBooks, EJournals}
import weco.pipeline.transformer.marc_common.generators.MarcTestRecord
import weco.pipeline.transformer.marc_common.models.MarcControlField

class MarcFormatTest extends AnyFunSpec with Matchers with OptionValues {
  describe(
    "Extracting format information from MARC Leader/07, 006/06 & 008 material specific details"
  ) {
    info("https://www.loc.gov/marc/bibliographic/bdleader.html")
    info("https://www.loc.gov/marc/bibliographic/bd006.html")
    info("https://www.loc.gov/marc/bibliographic/bd008s.html")

    it("returns an e-journal format when metadata indicates it") {
      val record = MarcTestRecord(
        leader = "00000cas a22000003a 4500",
        controlFields = Seq(
          MarcControlField("006", "m\\\\\\\\\\o\\\\d\\\\||||||")
        )
      )

      MarcFormat(record).value shouldBe EJournals
    }

    it("returns an e-book format when metadata indicates it") {
      val record = MarcTestRecord(
        leader = "00000cam a22000003a 4500",
        controlFields = Seq(
          MarcControlField("006", "m\\\\\\\\\\o\\\\d\\\\||||||")
        )
      )

      MarcFormat(record).value shouldBe EBooks
    }

    // We only care about the format ofe-journals and e-books at the moment
    // so we don't need to test for other formats. As this transformer
    // is only used in the context of the EBSCO transformer.
    it(
      "returns no format when metadata indicates neither e-journal nor e-book"
    ) {
      val record = MarcTestRecord(
        leader = "00000 am  2200289 a 4500",
        controlFields = Seq(
          MarcControlField("006", "m\\\\\\\\\\f\\\\d\\\\||||||")
        )
      )

      MarcFormat(record) shouldBe None
    }
  }
}
