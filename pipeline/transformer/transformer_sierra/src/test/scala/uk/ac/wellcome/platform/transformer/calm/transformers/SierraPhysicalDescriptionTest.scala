package uk.ac.wellcome.platform.transformer.calm.transformers

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.platform.transformer.calm.source.{
  MarcSubfield,
  SierraBibData,
  VarField
}
import uk.ac.wellcome.platform.transformer.calm.generators.{
  MarcGenerators,
  SierraDataGenerators
}

class SierraPhysicalDescriptionTest
    extends FunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  it("gets no physical description if there is no MARC field 300") {
    val field = varField(
      "563",
      MarcSubfield("b", "The edifying extent of early emus")
    )
    SierraPhysicalDescription(bibId, bibData(field)) shouldBe None
  }

  it("extracts physical description from MARC field 300 subfield $b") {
    val description = "Queuing quokkas quarrel about Quirinus Quirrell"
    val field = varField(
      "300",
      MarcSubfield("b", description),
      MarcSubfield("d", "The edifying extent of early emus"),
    )
    SierraPhysicalDescription(bibId, bibData(field)) shouldBe Some(description)
  }

  it(
    "extracts a physical description where there are multiple MARC field 300 $b") {
    val descriptionA = "The queer quolls quits and quarrels"
    val descriptionB = "A quintessential quadraped is quick"
    val expectedDescription = s"$descriptionA $descriptionB"
    val data = bibData(
      varField("300", MarcSubfield("b", descriptionA)),
      varField(
        "300",
        MarcSubfield("b", descriptionB),
        MarcSubfield("d", "Egad!  An early eagle is eating the earwig."),
      ),
    )
    SierraPhysicalDescription(bibId, data) shouldBe Some(expectedDescription)
  }

  it(
    f"extracts a physical description frorm MARC 300 subfields $$a, $$b and $$c") {
    val descriptionA = "The queer quolls quits and quarrels"
    val descriptionB = "A quintessential quadraped is quick"
    val descriptionC = "The edifying extent of early emus"
    val expectedDescription = s"$descriptionA $descriptionB $descriptionC"
    val data = bibData(
      varField("300", MarcSubfield("b", descriptionB)),
      varField("300", MarcSubfield("a", descriptionA)),
      varField("300", MarcSubfield("c", descriptionC)),
    )
    SierraPhysicalDescription(bibId, data) shouldBe Some(expectedDescription)
  }

  def bibId = createSierraBibNumber

  def bibData(varFields: VarField*): SierraBibData =
    createSierraBibDataWith(varFields = varFields.toList)

  def varField(tag: String, subfields: MarcSubfield*): VarField =
    createVarFieldWith(marcTag = tag, subfields = subfields.toList)
}
