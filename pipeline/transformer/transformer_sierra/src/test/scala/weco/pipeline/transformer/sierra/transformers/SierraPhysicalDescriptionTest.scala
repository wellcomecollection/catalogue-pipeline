package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.source_model.generators.{
  MarcGenerators,
  SierraDataGenerators
}
import weco.catalogue.source_model.sierra.marc.{MarcSubfield, VarField}
import weco.catalogue.source_model.sierra.SierraBibData

class SierraPhysicalDescriptionTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  it("gets no physical description if there is no MARC field 300") {
    val field = varField(
      "563",
      MarcSubfield("b", "The edifying extent of early emus")
    )
    SierraPhysicalDescription(bibData(field)) shouldBe None
  }

  it("transforms field 300 subfield $b") {
    val description = "Queuing quokkas quarrel about Quirinus Quirrell"
    val field = varField(
      "300",
      MarcSubfield("b", description),
      MarcSubfield("d", "The edifying extent of early emus"),
    )
    SierraPhysicalDescription(bibData(field)) shouldBe Some(description)
  }

  it("transforms multiple instances of field 300 $b") {
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
    SierraPhysicalDescription(data) shouldBe Some(expectedDescription)
  }

  it("uses field 300 ǂa, ǂb and ǂc") {
    val descriptionA = "The queer quolls quits and quarrels"
    val descriptionB = "A quintessential quadraped is quick"
    val descriptionC = "The edifying extent of early emus"
    val expectedDescription = s"$descriptionA $descriptionB $descriptionC"
    val data = bibData(
      varField("300", MarcSubfield("b", descriptionB)),
      varField("300", MarcSubfield("a", descriptionA)),
      varField("300", MarcSubfield("c", descriptionC)),
    )
    SierraPhysicalDescription(data) shouldBe Some(expectedDescription)
  }

  it("uses field 300 ǂa, ǂb, ǂc and ǂe") {
    val extent = "1 photograph :"
    val otherPhysicalDetails = "photonegative, glass ;"
    val dimensions = "glass 10.6 x 8 cm +"
    val accompanyingMaterial = "envelope"
    val data = bibData(
      varField("300", MarcSubfield("a", extent)),
      varField("300", MarcSubfield("b", otherPhysicalDetails)),
      varField("300", MarcSubfield("c", dimensions)),
      varField("300", MarcSubfield("e", accompanyingMaterial)),
    )
    SierraPhysicalDescription(data) shouldBe Some(
      "1 photograph : photonegative, glass ; glass 10.6 x 8 cm + envelope")
  }

  def bibData(varFields: VarField*): SierraBibData =
    createSierraBibDataWith(varFields = varFields.toList)

  def varField(tag: String, subfields: MarcSubfield*): VarField =
    createVarFieldWith(marcTag = tag, subfields = subfields.toList)
}
