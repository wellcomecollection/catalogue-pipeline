package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.marc.{Subfield, VarField}

class SierraPhysicalDescriptionTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators {

  it("gets no physical description if there is no MARC field 300") {
    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "563",
          subfields = List(
            Subfield("b", "The edifying extent of early emus")
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
          marcTag = "300",
          subfields = List(
            Subfield("b", description),
            Subfield("d", "The edifying extent of early emus")
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
          marcTag = "300",
          subfields = List(
            Subfield("b", description1)
          )
        ),
        VarField(
          marcTag = "300",
          subfields = List(
            Subfield("b", description2),
            Subfield("d", "Egad!  An early eagle is eating the earwig.")
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
          marcTag = "300",
          subfields = List(
            Subfield(tag = "a", content = "1 videocassette (VHS) (1 min.) :"),
            Subfield(tag = "b", content = "sound, color, PAL.")
          )
        ),
        VarField(
          marcTag = "300",
          subfields = List(
            Subfield(tag = "a", content = "1 DVD (1 min.) :"),
            Subfield(tag = "b", content = "sound, color")
          )
        )
      )
    )

    SierraPhysicalDescription(bibData) shouldBe Some(
      "1 videocassette (VHS) (1 min.) : sound, color, PAL.<br/>1 DVD (1 min.) : sound, color"
    )
  }

  it("uses field 300 ǂa, ǂb and ǂc") {
    val descriptionA = "The queer quolls quits and quarrels"
    val descriptionB = "A quintessential quadraped is quick"
    val descriptionC = "The edifying extent of early emus"
    val expectedDescription = s"$descriptionA $descriptionB $descriptionC"

    val bibData = createSierraBibDataWith(
      varFields = List(
        VarField(
          marcTag = "300",
          subfields = List(
            Subfield(tag = "a", content = descriptionA),
            Subfield(tag = "b", content = descriptionB),
            Subfield(tag = "c", content = descriptionC)
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
          marcTag = "300",
          subfields = List(
            Subfield(tag = "a", content = extent),
            Subfield(tag = "b", content = otherPhysicalDetails),
            Subfield(tag = "c", content = dimensions),
            Subfield(tag = "e", content = accompanyingMaterial)
          )
        )
      )
    )

    SierraPhysicalDescription(bibData) shouldBe Some(
      "1 photograph : photonegative, glass ; glass 10.6 x 8 cm + envelope"
    )
  }
}
