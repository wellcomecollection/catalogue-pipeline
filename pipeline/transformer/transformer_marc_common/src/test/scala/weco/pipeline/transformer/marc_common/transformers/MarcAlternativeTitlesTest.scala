package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.LoneElement
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks._
import weco.pipeline.transformer.marc_common.generators.MarcTestRecord
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}
import scala.util.Random
class MarcAlternativeTitlesTest
    extends AnyFunSpec
    with Matchers
    with LoneElement {

  describe("extracting alternative titles from 130, 240, 242, and 246 fields") {
    info("https://www.loc.gov/marc/bibliographic/bd130.html")
    info("https://www.loc.gov/marc/bibliographic/bd240.html")
    info("https://www.loc.gov/marc/bibliographic/bd246.html")
    info("https://www.loc.gov/marc/bibliographic/bd242.html")
    describe("returning nothing") {
      it(
        "does not extract alternative titles if 130, 240, 242, 246 are all absent"
      ) {
        MarcAlternativeTitles(
          MarcTestRecord(
            fields = Seq(
              MarcField(
                marcTag = "999",
                subfields = Seq(MarcSubfield(tag = "a", content = "mafeesh"))
              )
            )
          )
        ) shouldBe Nil
      }

      it("does not return an empty alternative title given an empty field") {
        MarcAlternativeTitles(
          MarcTestRecord(
            fields = Seq(
              MarcField(
                marcTag = "130",
                subfields = Seq(MarcSubfield(tag = "a", content = ""))
              )
            )
          )
        ) shouldBe Nil
      }

      it(
        "does not return an empty alternative title given a field whose content is entirely filtered out"
      ) {
        MarcAlternativeTitles(
          MarcTestRecord(
            fields = Seq(
              MarcField(
                marcTag = "246",
                subfields = Seq(MarcSubfield(tag = "5", content = "UkLW"))
              )
            )
          )
        ) shouldBe Nil
      }

      it("ignores 'caption title' fields, i.e. 246 fields with ind2=6") {
        MarcAlternativeTitles(
          MarcTestRecord(
            fields = Seq(
              MarcField(
                marcTag = "246",
                subfields =
                  Seq(MarcSubfield(tag = "a", content = "I am a caption")),
                indicator2 = "6"
              )
            )
          )
        ) shouldBe Nil
      }

    }
  }
  describe("extracting a single alternative title") {
    forAll(
      Table(
        "tag",
        "130",
        "240",
        "242",
        "246"
      )
    ) {
      tag =>
        describe(s"extracting an alternative title from $tag") {
          it(s"extracts an alternative tile from field $tag") {
            MarcAlternativeTitles(
              MarcTestRecord(
                fields = Seq(
                  MarcField(
                    marcTag = tag,
                    subfields =
                      Seq(MarcSubfield(tag = "a", content = "mafeesh"))
                  )
                )
              )
            ).loneElement shouldBe "mafeesh"
          }

          it(
            s"concatenates all subfields of $tag in document order to make the alternative title"
          ) {
            val shuffled = Random.shuffle(subfieldLists(tag))
            val subfields = shuffled.map(
              subtag => MarcSubfield(tag = subtag, content = subtag.toUpperCase)
            )
            val expectedTitle =
              shuffled.map(_.toUpperCase).mkString(" ")

            MarcAlternativeTitles(
              MarcTestRecord(
                fields = Seq(
                  MarcField(
                    marcTag = tag,
                    subfields = subfields
                  )
                )
              )
            ).loneElement shouldBe expectedTitle
          }
          if (tag == "246") {
            it("ignores subfield 246$5 if its value is UkLW") {
              info("$5UkLW is Wellcome Library-specific and should be omitted")
              info(
                "$5 is non-repeating, so this example should not exist in Real Life"
              )
              info(" but this test demonstrates that the existence oof $5UkLW")
              info(
                " does not impact the transformer's ability to extract anything else"
              )
              MarcAlternativeTitles(
                MarcTestRecord(
                  fields = Seq(
                    MarcField(
                      marcTag = tag,
                      subfields = Seq(
                        MarcSubfield(tag = "a", content = "Pinakes"),
                        MarcSubfield(tag = "5", content = "UkLW"),
                        MarcSubfield(tag = "5", content = "Mouseion")
                      )
                    )
                  )
                )
              ).loneElement shouldBe "Pinakes Mouseion"
            }
          }
        }
    }
  }
  describe("extracting multiple alternative titles") {
    it("extracts alternative titles from all relevant fields") {
      MarcAlternativeTitles(
        MarcTestRecord(
          fields = Seq(
            MarcField(
              marcTag = "130",
              subfields = Seq(
                MarcSubfield(
                  tag = "a",
                  content = "I'm very well acquainted too"
                )
              )
            ),
            MarcField(
              marcTag = "240",
              subfields = Seq(
                MarcSubfield(tag = "a", content = "with matters mathematical")
              )
            ),
            MarcField(
              marcTag = "246",
              subfields =
                Seq(MarcSubfield(tag = "a", content = "I understand equations"))
            ),
            MarcField(
              marcTag = "246",
              subfields = Seq(MarcSubfield(tag = "a", content = "both simple"))
            ),
            MarcField(
              marcTag = "240",
              subfields =
                Seq(MarcSubfield(tag = "a", content = "and quadratical"))
            ),
            MarcField(
              marcTag = "130",
              subfields = Seq(
                MarcSubfield(
                  tag = "a",
                  content =
                    "About binomial theorem I am teeming with a lot o' news"
                )
              )
            ),
            MarcField(
              marcTag = "242",
              subfields = Seq(
                MarcSubfield(
                  tag = "a",
                  content =
                    "Ikh hob a klugn kop un ikh farshtey Einstein's teyoriye"
                )
              )
            )
          )
        )
      ) should contain theSameElementsAs Seq(
        "I'm very well acquainted too",
        "with matters mathematical",
        "I understand equations",
        "both simple",
        "and quadratical",
        "About binomial theorem I am teeming with a lot o' news",
        "Ikh hob a klugn kop un ikh farshtey Einstein's teyoriye"
      )
    }
    it("does not return duplicate alternative titles") {
      MarcAlternativeTitles(
        MarcTestRecord(
          fields = Seq(
            MarcField(
              marcTag = "130",
              subfields = Seq(
                MarcSubfield(
                  tag = "a",
                  content =
                    "With many cheerful facts about the square of the hypotenuse"
                )
              )
            ),
            MarcField(
              marcTag = "240",
              subfields = Seq(
                MarcSubfield(
                  tag = "a",
                  content =
                    "With many cheerful facts about the square of the hypotenuse"
                )
              )
            ),
            MarcField(
              marcTag = "246",
              subfields = Seq(
                MarcSubfield(
                  tag = "a",
                  content =
                    "With many cheerful facts about the square of the hypotenuse"
                )
              )
            ),
            MarcField(
              marcTag = "242",
              subfields = Seq(
                MarcSubfield(
                  tag = "a",
                  content =
                    "With many cheerful facts about the square of the hypoten-potenuse"
                )
              )
            ),
            MarcField(
              marcTag = "246",
              subfields = Seq(
                MarcSubfield(
                  tag = "a",
                  content =
                    "With many cheerful facts about the square of the hypoten-potenuse"
                )
              )
            )
          )
        )
      ) should contain theSameElementsAs Seq(
        "With many cheerful facts about the square of the hypotenuse",
        "With many cheerful facts about the square of the hypoten-potenuse"
      )
    }
    it("only filters on ind2=6 for 246 fields") {
      info("246 with indicator2 is a caption title")
      info("this is not true of 130 and 240")
      val fields = Seq(
        "130" -> "I am not a caption",
        "246" -> "I am a caption",
        "240" -> "Nor am I",
        "242" -> "Heller ikkje meg"
      ) map {
        case (tag, content) =>
          MarcField(
            indicator2 = "6",
            marcTag = tag,
            subfields = Seq(
              MarcSubfield(
                tag = "a",
                content = content
              )
            )
          )
      }

      MarcAlternativeTitles(
        MarcTestRecord(fields = fields)
      ) should contain theSameElementsAs Seq(
        "I am not a caption",
        "Nor am I",
        "Heller ikkje meg"
      )
    }
  }

  private val subfieldLists = Map(
    "130" -> Seq(
      "a",
      "d",
      "f",
      "g",
      "h",
      "k",
      "l",
      "m",
      "n",
      "o",
      "p",
      "r",
      "s",
      "t",
      "0",
      "1",
      "2",
      "6",
      "7",
      "8"
    ),
    "240" -> Seq(
      "a",
      "d",
      "f",
      "g",
      "h",
      "k",
      "l",
      "m",
      "n",
      "o",
      "p",
      "r",
      "s",
      "0",
      "1",
      "2",
      "6",
      "7",
      "8"
    ),
    "246" -> Seq("a", "b", "f", "g", "h", "i", "n", "p", "5", "6", "7", "8"),
    "242" -> Seq("a", "b", "c", "h", "n", "p", "y")
  )
}
