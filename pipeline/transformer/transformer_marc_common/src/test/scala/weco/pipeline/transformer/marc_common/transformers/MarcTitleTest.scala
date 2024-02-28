package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pipeline.transformer.exceptions.ShouldNotTransformException
import weco.pipeline.transformer.marc_common.generators.MarcTestRecord
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}

class MarcTitleTest extends AnyFunSpec with Matchers {
  info("The title is extracted from the MARC 245 field")
  describe("finding the 245 field") {
    it("constructs a title from the content of the 245 field") {
      MarcTitle(
        MarcTestRecord(
          fields = Seq(
            marc245With(Seq(("a", "hello")))
          )
        )
      ).get shouldBe "hello"

    }
    it("uses the first 245 field if there are (erroneously) more than one") {
      // Ideally, this branch would throw an exception, but there are still some
      // records in Sierra that erroneously contain extra 245s. Once they have been fixed,
      // we can make it an error and move this test into the "can't create a title" block
      // All EBSCO content is expected to be compliant, and we should explicitly reject it if it is not.
      MarcTitle(
        MarcTestRecord(
          fields = Seq(
            MarcField(marcTag = "001"),
            marc245With(Seq(("a", "hello world"))),
            marc245With(Seq(("a", "hello dolly")))
          )
        )
      ).get shouldBe "hello world"
    }
  }

  describe("constructing the title from the subfields") {
    it("constructs the title from subfields a, b, c, h, n, p") {
      MarcTitle(
        MarcTestRecord(
          fields = Seq(
            marc245With(
              Seq(
                "a" -> "cyntaf",
                "b" -> "ail",
                "c" -> "trydydd",
                "h" -> "pedwerydd",
                "n" -> "pumed",
                "p" -> "chweched",
                "k" -> "saithfed"
              )
            )
          )
        )
      ).get shouldBe "cyntaf ail trydydd pedwerydd pumed chweched"
    }
    it("constructs the title from the chosen subfields in document order") {
      MarcTitle(
        MarcTestRecord(
          fields = Seq(
            marc245With(
              Seq(
                "h" -> "cyntaf",
                "p" -> "ail",
                "c" -> "trydydd",
                "n" -> "pedwerydd",
                "a" -> "pumed",
                "b" -> "chweched",
                "k" -> "saithfed"
              )
            )
          )
        )
      ).get shouldBe "cyntaf ail trydydd pedwerydd pumed chweched"
    }
    it("supports repeated subfields") {
      info(
        "technically, only p and n can be repeated, but the transformer is not picky about that"
      )
      MarcTitle(
        MarcTestRecord(
          fields = Seq(
            marc245With(
              Seq(
                "n" -> "cyntaf",
                "p" -> "ail",
                "n" -> "trydydd",
                "p" -> "pedwerydd",
                "h" -> "pumed",
                "p" -> "chweched",
                "p" -> "saithfed"
              )
            )
          )
        )
      ).get shouldBe "cyntaf ail trydydd pedwerydd pumed chweched saithfed"
    }

    it("ignores a trailing 'h' subfield") {
      MarcTitle(
        MarcTestRecord(
          fields = Seq(
            marc245With(
              Seq(
                "a" -> "cyntaf",
                "h" -> "ail",
                "p" -> "trydydd",
                "h" -> "ignore me, I'm not here!"
              )
            )
          )
        )
      ).get shouldBe "cyntaf ail trydydd"
    }

    it("discards square bracket content in h subfields") {
      MarcTitle(
        MarcTestRecord(
          fields = Seq(
            marc245With(
              Seq(
                "a" -> "cyntaf [un]",
                "h" -> "ail [dau]",
                "p" -> "trydydd"
              )
            )
          )
        )
      ).get shouldBe "cyntaf [un] ail trydydd"
    }

  }

  describe("throws a ShouldNotTransformException if it can't create a title") {
    it("throws if there is no MARC field 245") {
      val caught = intercept[ShouldNotTransformException] {
        MarcTitle(MarcTestRecord(fields = Nil))
      }
      caught.getMessage should startWith(
        "Could not find field 245 to create title"
      )
    }

    it("throws if there are no subfields") {
      val caught = intercept[ShouldNotTransformException] {
        MarcTitle(MarcTestRecord(fields = Seq(MarcField(marcTag = "245"))))
      }
      caught.getMessage should startWith(
        "No subfields in field 245 for constructing the title"
      )
    }

    it("throws if there are no *suitable* subfields") {
      val caught = intercept[ShouldNotTransformException] {
        MarcTitle(
          MarcTestRecord(
            fields = Seq(
              marc245With(Seq(("7", "the back of a lorry")))
            )
          )
        )
      }
      caught.getMessage should startWith(
        "No subfields in field 245 for constructing the title"
      )
    }
    it("throws if the only suitable subfield has tag h") {
      info("although h is a suitable subfield, a trailing h is discarded")
      info("as a result, this is the same as there being no subfields at all")
      val caught = intercept[ShouldNotTransformException] {
        MarcTitle(
          MarcTestRecord(
            fields = Seq(
              marc245With(Seq(("h", "the back of a lorry"))),
              marc245With(Seq(("7", "the back of a lorry")))
            )
          )
        )
      }
      caught.getMessage should startWith(
        "No subfields in field 245 for constructing the title"
      )
    }
  }

  private def marc245With(subfields: Seq[(String, String)]) = MarcField(
    marcTag = "245",
    subfields = subfields map {
      case (tag, content) => MarcSubfield(tag = tag, content = content)
    }
  )
}
