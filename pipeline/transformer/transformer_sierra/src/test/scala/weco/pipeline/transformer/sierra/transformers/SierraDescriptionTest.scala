package weco.pipeline.transformer.sierra.transformers

import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.{Subfield, VarField}

class SierraDescriptionTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators {

  it("gets a description from a bib with a single instance of MARC 520") {
    val description = "A panolopy of penguins perching on a python."
    val expectedDescription = s"<p>$description</p>"

    assertFindsCorrectDescription(
      varFields = List(
        VarField(
          marcTag = "520",
          subfields = List(
            Subfield(tag = "a", content = description)
          )
        )
      ),
      expectedDescription = Some(expectedDescription)
    )
  }

  it("gets a description from a bib with multiple instances of MARC 520") {
    val description1 = "A malcontent marc minion."
    val description2 = "A fresh fishy fruit."

    val expectedDescription = s"<p>$description1</p>\n<p>$description2</p>"

    assertFindsCorrectDescription(
      varFields = List(
        VarField(
          marcTag = "520",
          subfields = List(
            Subfield(tag = "a", content = description1)
          )
        ),
        VarField(
          marcTag = "520",
          subfields = List(
            Subfield(tag = "a", content = description2)
          )
        )
      ),
      expectedDescription = Some(expectedDescription)
    )
  }

  it("gets a description from a MARC tag 520 with ǂa and ǂb") {
    val description = "A panolopy of penguins perching on a python."
    val summaryDescription = "A fracas of frolicking frogs on futons."

    val expectedDescription = s"<p>$description $summaryDescription</p>"

    assertFindsCorrectDescription(
      varFields = List(
        VarField(
          marcTag = "520",
          subfields = List(
            Subfield(tag = "a", content = description),
            Subfield(tag = "b", content = summaryDescription)
          )
        )
      ),
      expectedDescription = Some(expectedDescription)
    )
  }

  it("gets a description from subfields ǂa and ǂc") {
    // This is based on b20646604, as retrieved 15 September 2022
    assertFindsCorrectDescription(
      varFields = List(
        VarField(
          marcTag = "520",
          subfields = List(
            Subfield(tag = "a", content = "\"This book is about the ethics of nursing and midwifery\""),
            Subfield(tag = "c", content = "Provided by publisher.")
          )
        )
      ),
      expectedDescription = Some("<p>\"This book is about the ethics of nursing and midwifery\" Provided by publisher.</p>")
    )
  }

  describe("getting URLs from MARC 520 ǂu") {
    val description = "Picking particular pears in Poland."
    val summaryDescription = "Selecting sumptious starfruit in Spain."

    it("wraps a single URL in <a> tags") {
      val url = "https://fruitpicking.org/"

      val expectedDescription =
        s"""<p>$description $summaryDescription <a href="$url">$url</a></p>"""

      assertFindsCorrectDescription(
        varFields = List(
          VarField(
            marcTag = "520",
            subfields = List(
              Subfield(tag = "a", content = description),
              Subfield(tag = "b", content = summaryDescription),
              Subfield(tag = "u", content = url)
            )
          )
        ),
        expectedDescription = Some(expectedDescription)
      )
    }

    it("wraps multiple URLs in <a> tags") {
      val url1 = "https://fruitpicking.org/"
      val url2 = "https://fruitpicking.org/"

      val expectedDescription =
        s"""<p>$description $summaryDescription <a href="$url1">$url1</a> <a href="$url2">$url2</a></p>"""

      assertFindsCorrectDescription(
        varFields = List(
          VarField(
            marcTag = "520",
            subfields = List(
              Subfield(tag = "a", content = description),
              Subfield(tag = "b", content = summaryDescription),
              Subfield(tag = "u", content = url1),
              Subfield(tag = "u", content = url2)
            )
          )
        ),
        expectedDescription = Some(expectedDescription)
      )
    }

    it(
      "does not wrap the contents of ǂu in <a> tags if it doesn't look like a URL") {
      val url1 = "https://fruitpicking.org/"
      val uContents = "A website about fruitpicking"

      val expectedDescription =
        s"""<p>$description $summaryDescription <a href="$url1">$url1</a> $uContents</p>"""

      assertFindsCorrectDescription(
        varFields = List(
          VarField(
            marcTag = "520",
            subfields = List(
              Subfield(tag = "a", content = description),
              Subfield(tag = "b", content = summaryDescription),
              Subfield(tag = "u", content = url1),
              Subfield(tag = "u", content = uContents)
            )
          )
        ),
        expectedDescription = Some(expectedDescription)
      )
    }
  }

  it("does not get a description if MARC field 520 is absent") {
    assertFindsCorrectDescription(
      varFields = List(
        VarField(marcTag = Some("666"))
      ),
      expectedDescription = None
    )
  }

  private def assertFindsCorrectDescription(
    varFields: List[VarField],
    expectedDescription: Option[String]
  ): Assertion = {
    val actualDescription = SierraDescription(
      bibId = createSierraBibNumber,
      bibData = createSierraBibDataWith(varFields = varFields)
    )
    actualDescription shouldBe expectedDescription
  }
}
