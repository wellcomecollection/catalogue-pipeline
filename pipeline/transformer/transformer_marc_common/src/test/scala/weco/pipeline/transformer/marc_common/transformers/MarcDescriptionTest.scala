package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.LoneElement
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pipeline.transformer.exceptions.ShouldNotTransformException
import weco.pipeline.transformer.marc_common.generators.MarcTestRecord
import weco.pipeline.transformer.marc_common.logging.LoggingContext
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}
import org.scalatest.prop.TableDrivenPropertyChecks._

class MarcDescriptionTest extends AnyFunSpec with Matchers with LoneElement {
  private implicit val ctx: LoggingContext = LoggingContext("")

  describe("Extracting a description from MARC 520") {
    info("https://www.loc.gov/marc/bibliographic/bd520.html")
    describe("returning nothing") {
      it("returns None if the record does not contain MARC 520") {
        MarcDescription(MarcTestRecord(fields = Nil)) shouldBe None
      }

      it("returns None if no 520 fields contain subfields a, b, c, or u") {
        MarcDescription(
          MarcTestRecord(fields =
            Seq(
              MarcField(
                marcTag = "520",
                subfields = Seq(MarcSubfield("7", "not me!"))
              )
            )
          )
        ) shouldBe None

      }
      describe(
        "non-repeating subfields"
      ) {
        info("a, b and c are all non-repeating")

        forAll(
          Table(
            "subfieldCode",
            "a",
            "b",
            "c"
          )
        ) {
          subfieldCode =>
            it(
              s"throws when a 520 field has multiple ǂ$subfieldCode subfields"
            ) {
              // AFAICS, there are no existing records with illegal repeating subfields
              assertThrows[ShouldNotTransformException] {
                MarcDescription(
                  MarcTestRecord(fields =
                    Seq(
                      MarcField(
                        marcTag = "520",
                        subfields = Seq(
                          MarcSubfield(subfieldCode, "Cyntaf"),
                          MarcSubfield(subfieldCode, "Ail")
                        )
                      )
                    )
                  )
                )
              }
            }
        }
      }
    }
    describe("returning descriptions") {
      info(
        "each 520 field corresponds to one paragraph of the whole description"
      )
      info("each paragraph is marked up as an HTML <p> element")
      it("returns a description from 520ǂa on its own") {
        MarcDescription(
          MarcTestRecord(fields =
            Seq(
              MarcField(
                marcTag = "520",
                subfields = Seq(
                  MarcSubfield(
                    "a",
                    "Descriptions..are definitions of a more lax and fanciful kind."
                  )
                )
              )
            )
          )
        ).get shouldBe "<p>Descriptions..are definitions of a more lax and fanciful kind.</p>"
      }

      it("concatenates subfields a,b,c,u") {
        MarcDescription(
          MarcTestRecord(fields =
            Seq(
              MarcField(
                marcTag = "520",
                subfields = Seq(
                  MarcSubfield(
                    "a",
                    "As there is a fine name, now-a-days, for every thing,"
                  ),
                  MarcSubfield(
                    "b",
                    "I suppose that ‘Hygeist’ is the polite description of quack doctor."
                  ),
                  MarcSubfield("c", "London Magazine, 1826"),
                  MarcSubfield("u", "http://example.com/")
                )
              )
            )
          )
        ).get shouldBe "<p>As there is a fine name, now-a-days, for every thing, I suppose that ‘Hygeist’ is the polite description of quack doctor. London Magazine, 1826 <a href=\"http://example.com/\">http://example.com/</a></p>"
      }

      it("forces the order of subfields to be a,b,c,u") {
        MarcDescription(
          MarcTestRecord(fields =
            Seq(
              MarcField(
                marcTag = "520",
                subfields = Seq(
                  MarcSubfield("u", "http://example.com/"),
                  MarcSubfield(
                    "b",
                    "I suppose that ‘Hygeist’ is the polite description of quack doctor."
                  ),
                  MarcSubfield(
                    "a",
                    "As there is a fine name, now-a-days, for every thing,"
                  ),
                  MarcSubfield("c", "London Magazine, 1826")
                )
              )
            )
          )
        ).get shouldBe "<p>As there is a fine name, now-a-days, for every thing, I suppose that ‘Hygeist’ is the polite description of quack doctor. London Magazine, 1826 <a href=\"http://example.com/\">http://example.com/</a></p>"
      }

      it("concatenates multiple 520ǂu subfields") {
        MarcDescription(
          MarcTestRecord(fields =
            Seq(
              MarcField(
                marcTag = "520",
                subfields = Seq(
                  MarcSubfield(
                    "a",
                    "For her owne person, It beggerd all discription."
                  ),
                  MarcSubfield("u", "http://example.com/6347939"),
                  MarcSubfield("u", "http://example.com/5877688")
                )
              )
            )
          )
        ).get shouldBe "<p>For her owne person, It beggerd all discription. <a href=\"http://example.com/6347939\">http://example.com/6347939</a> <a href=\"http://example.com/5877688\">http://example.com/5877688</a></p>"
      }

      it("trims superfluous whitespace surrounding the description") {
        MarcDescription(
          MarcTestRecord(fields =
            Seq(
              MarcField(
                marcTag = "520",
                subfields = Seq(
                  MarcSubfield(
                    "a",
                    "\t   Shapen in maner of a lop-webbe aftur the olde descripcioun.   "
                  )
                )
              )
            )
          )
        ).get shouldBe "<p>Shapen in maner of a lop-webbe aftur the olde descripcioun.</p>"
      }
      it(
        "concatenates multiple 520 fields into one string"
      ) {
        MarcDescription(
          MarcTestRecord(fields =
            Seq(
              MarcField(
                marcTag = "520",
                subfields = Seq(
                  MarcSubfield(
                    "a",
                    "This place perfectly answers the Description of the Elysian fields"
                  )
                )
              ),
              MarcField(
                marcTag = "520",
                subfields = Seq(
                  MarcSubfield(
                    "a",
                    "Wherein also we have interspersed, under the several heads of villains, such descriptions and cautions as may better serve to promote this good end."
                  )
                )
              )
            )
          )
        ).get shouldBe "<p>This place perfectly answers the Description of the Elysian fields</p>" +
          "\n<p>Wherein also we have interspersed, under the several heads of villains, such descriptions and cautions as may better serve to promote this good end.</p>"
      }

    }
  }
}
