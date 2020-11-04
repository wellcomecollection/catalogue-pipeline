package uk.ac.wellcome.platform.transformer.sierra.transformers

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

  it(
    "extracts a work description where MARC field 520 with subfield a is populated") {
    val description = "A panolopy of penguins perching on a python."

    assertFindsCorrectDescription(
      varFields = List(
        createVarFieldWith(
          marcTag = "520",
          subfields = List(
            MarcSubfield(tag = "a", content = description)
          )
        )
      ),
      expectedDescription = Some(s"<p>$description</p>")
    )
  }

  it("extracts a work description where there are multiple MARC field 520") {
    val description1 = "A malcontent marc minion."
    val description2 = "A fresh fishy fruit."
    val summaryDescription2 = "A case of colloidal coffee capsules."

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
            MarcSubfield(tag = "a", content = description2),
            MarcSubfield(tag = "b", content = summaryDescription2)
          )
        )
      ),
      expectedDescription = Some(s"<p>$description1</p>\n<p>$description2</p>\n<p>$summaryDescription2</p>")
    )
  }

  it(
    "extracts a work description where MARC field 520 with subfield a and b are populated") {
    val description = "A panolopy of penguins perching on a python."
    val summaryDescription = "A fracas of frolicking frogs on futons."

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
      expectedDescription = Some(s"<p>$description $summaryDescription</p>")
    )
  }

  it("does not extract a work description where MARC field 520 is absent") {
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
  ) = {
    val bibData = createSierraBibDataWith(varFields = varFields)
    val bibId = createSierraBibNumber
    SierraDescription(bibId, bibData) shouldBe expectedDescription
  }
}
