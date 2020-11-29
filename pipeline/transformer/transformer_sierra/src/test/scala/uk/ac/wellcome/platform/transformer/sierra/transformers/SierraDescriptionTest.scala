package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.platform.transformer.sierra.source.{
  MarcSubfield,
  VarField
}
import uk.ac.wellcome.platform.transformer.sierra.generators.{
  MarcGenerators,
  SierraDataGenerators
}

class SierraDescriptionTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  it("gets a description from a bib with a single instance of MARC 520") {
    val description = "A panolopy of penguins perching on a python."
    val expectedDescription = s"<p>$description</p>"

    assertFindsCorrectDescription(
      varFields = List(
        createVarFieldWith(
          marcTag = "520",
          subfields = List(
            MarcSubfield(tag = "a", content = description)
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
        createVarFieldWith(
          marcTag = "520",
          subfields = List(
            MarcSubfield(tag = "a", content = description1)
          )
        ),
        createVarFieldWith(
          marcTag = "520",
          subfields = List(
            MarcSubfield(tag = "a", content = description2)
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
        createVarFieldWith(
          marcTag = "520",
          subfields = List(
            MarcSubfield(tag = "a", content = description),
            MarcSubfield(tag = "b", content = summaryDescription)
          )
        )
      ),
      expectedDescription = Some(expectedDescription)
    )
  }

  it("does not get a description if MARC field 520 is absent") {
    assertFindsCorrectDescription(
      varFields = List(
        createVarFieldWith(marcTag = "666")
      ),
      expectedDescription = None
    )
  }

  private def assertFindsCorrectDescription(
    varFields: List[VarField],
    expectedDescription: Option[String]
  ): Assertion = {
    val actualDescription = SierraDescription(
      createSierraBibDataWith(varFields = varFields))
    actualDescription shouldBe expectedDescription
  }
}
