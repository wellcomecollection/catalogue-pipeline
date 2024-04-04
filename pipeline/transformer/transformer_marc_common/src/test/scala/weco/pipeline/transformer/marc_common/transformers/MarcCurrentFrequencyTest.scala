package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import weco.pipeline.transformer.marc_common.generators.MarcTestRecord
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}

class MarcCurrentFrequencyTest extends AnyFunSpec with Matchers {
  describe("extracting current frequency information from MARC 310") {
    info("https://www.loc.gov/marc/bibliographic/bd310.html")
    describe("returning None") {
      it("returns None if a record doesn't use MARC 310") {
        MarcCurrentFrequency(
          MarcTestRecord(fields =
            Seq(
              MarcField(
                "250",
                subfields = Seq(MarcSubfield(tag = "a", content = "First!"))
              )
            )
          )
        ) shouldBe None
      }

      it("returns None if there no a or b subfields in MARC 310") {
        MarcCurrentFrequency(
          MarcTestRecord(fields =
            Seq(
              MarcField(
                "310",
                subfields = Seq(
                  MarcSubfield(tag = "1", content = "Cyntaf!"),
                  MarcSubfield(tag = "6", content = "Ail!")
                )
              )
            )
          )
        ) shouldBe None
      }
    }

    describe("returning a value") {
      forAll(
        Table(
          "subfieldTag",
          "a",
          "b"
        )
      ) {
        subfieldTag =>
          it(s"returns the content of ǂ$subfieldTag") {
            MarcCurrentFrequency(
              MarcTestRecord(fields =
                Seq(
                  MarcField(
                    "310",
                    subfields =
                      Seq(MarcSubfield(tag = subfieldTag, content = "Unwaith"))
                  )
                )
              )
            ).get shouldBe "Unwaith"
          }
      }

      it("concatenates the content of ǂa and ǂb") {
        MarcCurrentFrequency(
          MarcTestRecord(fields =
            Seq(
              MarcField(
                "310",
                subfields = Seq(
                  MarcSubfield(tag = "a", content = "Every Nowruz"),
                  MarcSubfield(tag = "b", content = "2024-03-20")
                )
              )
            )
          )
        ).get shouldBe "Every Nowruz 2024-03-20"
      }
      it("concatenates multiple 310s") {
        MarcCurrentFrequency(
          MarcTestRecord(fields =
            Seq(
              MarcField(
                "310",
                subfields = Seq(
                  MarcSubfield(tag = "a", content = "Every Nowruz")
                )
              ),
              MarcField(
                "310",
                subfields = Seq(
                  MarcSubfield(tag = "a", content = "Whenever I feel like it")
                )
              )
            )
          )
        ).get shouldBe "Every Nowruz Whenever I feel like it"
      }
    }

  }

}
