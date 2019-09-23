package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.platform.transformer.sierra.source.MarcSubfield
import uk.ac.wellcome.platform.transformer.sierra.generators.{
  MarcGenerators,
  SierraDataGenerators
}

class SierraPhysicalDescriptionTest
    extends FunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  it(
    "gets no physical description if there is no MARC field 300 with subfield $b") {
    val bibData = createSierraBibDataWith(varFields = List())
    SierraPhysicalDescription(createSierraBibNumber, bibData) shouldBe None
  }

  it("extracts physical description from MARC field 300 subfield $b") {
    val expectedDescription = "Queuing quokkas quarrel about Quirinus Quirrell"

    val varFields = List(
      createVarFieldWith(
        marcTag = "300",
        subfields = List(
          MarcSubfield(
            tag = "a",
            content = "The edifying extent of early emus"
          ),
          MarcSubfield(
            tag = "b",
            content = expectedDescription
          )
        )
      )
    )

    val bibData = createSierraBibDataWith(varFields = varFields)
    val bibId = createSierraBibNumber
    SierraPhysicalDescription(bibId, bibData) shouldBe Some(expectedDescription)
  }

  it(
    "extracts a physical description where there are multiple MARC field 300 $b") {
    val physicalDescription1 = "The queer quolls quits and quarrels"
    val physicalDescription2 = "A quintessential quadraped is quick"

    val expectedDescription =
      s"$physicalDescription1\n\n$physicalDescription2"

    val varFields = List(
      createVarFieldWith(
        marcTag = "300",
        subfields = List(
          MarcSubfield(
            tag = "b",
            content = physicalDescription1
          )
        )
      ),
      createVarFieldWith(
        marcTag = "300",
        subfields = List(
          MarcSubfield(
            tag = "a",
            content = "Egad!  An early eagle is eating the earwig."
          ),
          MarcSubfield(
            tag = "b",
            content = physicalDescription2
          )
        )
      )
    )

    val bibData = createSierraBibDataWith(varFields = varFields)
    val bibId = createSierraBibNumber
    SierraPhysicalDescription(bibId, bibData) shouldBe Some(expectedDescription)
  }
}
