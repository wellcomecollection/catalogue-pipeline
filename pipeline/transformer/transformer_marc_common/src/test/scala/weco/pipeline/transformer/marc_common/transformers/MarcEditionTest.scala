package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.LoneElement
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pipeline.transformer.marc_common.generators.MarcTestRecord
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}

class MarcEditionTest extends AnyFunSpec with Matchers with LoneElement {
  describe("Extracting edition information from MARC 250") {
    info("https://www.loc.gov/marc/bibliographic/bd250.html")

    it("returns the edition statement from ǂa") {
      val record = MarcTestRecord(fields =
        Seq(
          MarcField(
            "250",
            subfields = Seq(MarcSubfield(tag = "a", content = "First!"))
          )
        )
      )
      MarcEdition(record).get shouldBe "First!"
    }

    it("concatenates multiple 250ǂa values to make a single string") {
      info("250 is repeatable, ǂa is non-repeatable")
      val record = MarcTestRecord(fields =
        Seq(
          MarcField(
            "250",
            subfields = Seq(MarcSubfield(tag = "a", content = "Cyntaf"))
          ),
          MarcField(
            "250",
            subfields = Seq(MarcSubfield(tag = "a", content = "Ail"))
          )
        )
      )
      MarcEdition(record).get shouldBe "Cyntaf Ail"

    }
  }

  describe("when there is no viable Edition Statement in the source") {
    it("returns None if there are no 250 fields") {
      val record = MarcTestRecord(fields =
        Seq(
          MarcField(
            "999",
            subfields = Seq(MarcSubfield(tag = "a", content = "First!"))
          )
        )
      )
      MarcEdition(record) shouldBe None
    }

    it("returns None if there are no 250ǂa subfields") {
      val record = MarcTestRecord(fields =
        Seq(
          MarcField(
            "250",
            subfields = Seq(MarcSubfield(tag = "6", content = "First!"))
          )
        )
      )
      MarcEdition(record) shouldBe None
    }
    it("returns None if 250ǂa is devoid of useful content") {
      val record = MarcTestRecord(fields =
        Seq(
          MarcField(
            "250",
            subfields = Seq(MarcSubfield(tag = "a", content = " "))
          )
        )
      )
      MarcEdition(record) shouldBe None
    }
  }
}
