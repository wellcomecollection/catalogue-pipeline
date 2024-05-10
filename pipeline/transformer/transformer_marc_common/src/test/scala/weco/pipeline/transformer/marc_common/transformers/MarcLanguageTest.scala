package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.LoneElement
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.languages.Language
import weco.pipeline.transformer.marc_common.generators.MarcTestRecord
import weco.pipeline.transformer.marc_common.models.MarcControlField

class MarcLanguageTest extends AnyFunSpec with Matchers with LoneElement {
  describe("Extracting edition information from MARC 008") {
    info("https://www.loc.gov/marc/bibliographic/bd008a.html")
    val englishLanguage = Language("eng", "English")

    it("returns the language from 008") {
      val record = MarcTestRecord(controlFields =
        Seq(
          MarcControlField("008", "961009c19339999ilubr pso 0 a0eng c")
        )
      )
      MarcLanguage(record) shouldBe Some(englishLanguage)
    }

    it("returns None, where the language is missing") {
      val record = MarcTestRecord(controlFields =
        Seq(
          MarcControlField("008", "240424nuuuuuuuuxx |||| o|||||||||||||||d")
        )
      )
      MarcLanguage(record) shouldBe None
    }
  }
}
