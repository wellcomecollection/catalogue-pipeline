package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.LoneElement
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pipeline.transformer.exceptions.ShouldNotTransformException
import weco.pipeline.transformer.marc_common.generators.MarcTestRecord
import weco.pipeline.transformer.marc_common.logging.LoggingContext
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}

class MarcDesignationTest extends AnyFunSpec with Matchers with LoneElement {
  private implicit val ctx: LoggingContext = LoggingContext("")

  describe("Extracting edition information from MARC 362") {
    info("https://www.loc.gov/marc/bibliographic/bd362.html")
    describe("returning nothing") {
      it("returns Nil if the record does not contain MARC 362") {
        MarcDesignation(MarcTestRecord(fields = Nil)) shouldBe Nil
      }
      it("returns Nil if no 362 fields contain ǂa") {
        MarcDesignation(
          MarcTestRecord(fields =
            Seq(
              MarcField(
                marcTag = "362",
                subfields = Seq(MarcSubfield("z", "not me!"))
              )
            )
          )
        ) shouldBe Nil

      }
      it(
        "raises an exception if multiple ǂa subfields are present on one 362"
      ) {
        info("ǂa is a non-repeating")
        assertThrows[ShouldNotTransformException] {
          MarcDesignation(
            MarcTestRecord(fields =
              Seq(
                MarcField(
                  marcTag = "362",
                  subfields = Seq(
                    MarcSubfield("a", "Cyntaf"),
                    MarcSubfield("a", "Ail")
                  )
                )
              )
            )
          )
        }
      }
    }
    describe("returning designation strings") {
      it("returns a designation string from 362ǂa") {
        MarcDesignation(
          MarcTestRecord(fields =
            Seq(
              MarcField(
                marcTag = "362",
                subfields = Seq(MarcSubfield("a", "TWKE-4"))
              )
            )
          )
        ).loneElement shouldBe "TWKE-4"
      }
      it("trims superfluous whitespace surrounding the designation") {
        MarcDesignation(
          MarcTestRecord(fields =
            Seq(
              MarcField(
                marcTag = "362",
                subfields = Seq(MarcSubfield("a", "   GSV\t"))
              )
            )
          )
        ).loneElement shouldBe "GSV"
      }
      it(
        "returns multiple designation strings if 362 is present multiple times"
      ) {
        MarcDesignation(
          MarcTestRecord(fields =
            Seq(
              MarcField(
                marcTag = "362",
                subfields = Seq(MarcSubfield("a", "VFP"))
              ),
              MarcField(
                marcTag = "362",
                subfields = Seq(MarcSubfield("a", "GCU"))
              )
            )
          )
        ) should contain theSameElementsAs Seq("VFP", "GCU")
      }

    }
  }
}
