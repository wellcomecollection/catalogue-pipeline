package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.source_model.sierra.marc.{MarcSubfield, VarField}
import weco.sierra.generators.{MarcGenerators, SierraDataGenerators}

class SierraPhysicalDescriptionTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {

  it("gets no physical description if there is no MARC field 300") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = Some("563"),
          subfields = List(
            MarcSubfield("b", "The edifying extent of early emus")
          )
        )
      )
    )

    SierraPhysicalDescription(bibData) shouldBe None
  }

  it("transforms field 300 subfield $b") {
    val description = "Queuing quokkas quarrel about Queen Quince"

    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = Some("300"),
          subfields = List(
            MarcSubfield("b", description),
            MarcSubfield("d", "The edifying extent of early emus"),
          )
        )
      )
    )

    SierraPhysicalDescription(bibData) shouldBe Some(description)
  }

  it("transforms multiple instances of field 300 $b") {
    val description1 = "The queer quolls quits and quarrels"
    val description2 = "A quintessential quadraped is quick"
    val expectedDescription = s"$description1<br/>$description2"

    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = Some("300"),
          subfields = List(
            MarcSubfield("b", description1)
          )
        ),
        VarField(
          marcTag = Some("300"),
          subfields = List(
            MarcSubfield("b", description2),
            MarcSubfield("d", "Egad!  An early eagle is eating the earwig."),
          )
        )
      )
    )

    SierraPhysicalDescription(bibData) shouldBe Some(expectedDescription)
  }

  it("transforms multiple instances of field 300") {
    // This is based on Sierra record b16768759
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = Some("300"),
          subfields = List(
            MarcSubfield(
              tag = "a",
              content = "1 videocassette (VHS) (1 min.) :"),
            MarcSubfield(tag = "b", content = "sound, color, PAL."),
          )
        ),
        VarField(
          marcTag = Some("300"),
          subfields = List(
            MarcSubfield(tag = "a", content = "1 DVD (1 min.) :"),
            MarcSubfield(tag = "b", content = "sound, color"),
          )
        )
      )
    )

    SierraPhysicalDescription(bibData) shouldBe Some(
      "1 videocassette (VHS) (1 min.) : sound, color, PAL.<br/>1 DVD (1 min.) : sound, color")
  }

  it("uses field 300 ǂa, ǂb and ǂc") {
    val descriptionA = "The queer quolls quits and quarrels"
    val descriptionB = "A quintessential quadraped is quick"
    val descriptionC = "The edifying extent of early emus"
    val expectedDescription = s"$descriptionA $descriptionB $descriptionC"

    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = Some("300"),
          subfields = List(
            MarcSubfield(tag = "a", content = descriptionA),
            MarcSubfield(tag = "b", content = descriptionB),
            MarcSubfield(tag = "c", content = descriptionC),
          )
        )
      )
    )

    SierraPhysicalDescription(bibData) shouldBe Some(expectedDescription)
  }

  it("uses field 300 ǂa, ǂb, ǂc and ǂe") {
    val extent = "1 photograph :"
    val otherPhysicalDetails = "photonegative, glass ;"
    val dimensions = "glass 10.6 x 8 cm +"
    val accompanyingMaterial = "envelope"

    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = Some("300"),
          subfields = List(
            MarcSubfield(tag = "a", content = extent),
            MarcSubfield(tag = "b", content = otherPhysicalDetails),
            MarcSubfield(tag = "c", content = dimensions),
            MarcSubfield(tag = "e", content = accompanyingMaterial),
          )
        )
      )
    )

    SierraPhysicalDescription(bibData) shouldBe Some(
      "1 photograph : photonegative, glass ; glass 10.6 x 8 cm + envelope")
  }
}
