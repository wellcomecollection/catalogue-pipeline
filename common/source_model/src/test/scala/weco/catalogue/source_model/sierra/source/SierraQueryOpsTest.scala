package weco.catalogue.source_model.sierra.source

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.source_model.generators.{
  MarcGenerators,
  SierraDataGenerators
}
import weco.catalogue.source_model.sierra.marc.{MarcSubfield, VarField}

class SierraQueryOpsTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators
    with SierraQueryOps {

  describe("ItemDataOps") {
    describe("displayNote") {
      it("returns None if there are no varfields with field tag n") {
        val item = createSierraItemData

        item.displayNote shouldBe None
      }

      it("finds the content from a single field tag n") {
        val item = createSierraItemDataWith(
          varFields = List(
            VarField(fieldTag = Some("n"), content = Some("Offsite"))
          )
        )

        item.displayNote shouldBe Some("Offsite")
      }

      it("finds the content from the subfields on field tag n") {
        val item = createSierraItemDataWith(
          varFields = List(
            VarField(
              fieldTag = Some("n"),
              subfields = List(
                MarcSubfield(tag = "a", content = "Part of:"),
                MarcSubfield(tag = "c", content = "a special collection"),
              ))
          )
        )

        item.displayNote shouldBe Some("Part of: a special collection")
      }

      it("removes any whitespace") {
        val item = createSierraItemDataWith(
          varFields = List(
            VarField(
              fieldTag = Some("n"),
              content = Some(" Conserved (2016)")
            )
          )
        )

        item.displayNote shouldBe Some("Conserved (2016)")
      }
    }
  }

  it("finds the varfields with given tags") {
    val varFields = List(
      createVarFieldWith(marcTag = "0", content = Some("Field 0A")),
      createVarFieldWith(marcTag = "1", content = Some("Field 1")),
      createVarFieldWith(marcTag = "0", content = Some("Field 0B")),
    )

    val bibData = createSierraBibDataWith(varFields = varFields)

    bibData.varfieldsWithTag("0") shouldBe List(varFields(0), varFields(2))
    bibData.varfieldsWithTag("1") shouldBe List(varFields(1))

    bibData.varfieldsWithTags("0") shouldBe List(varFields(0), varFields(2))
    bibData.varfieldsWithTags("1") shouldBe List(varFields(1))
    bibData.varfieldsWithTags("0", "1") shouldBe varFields
    bibData.varfieldsWithTags("1", "0") shouldBe varFields
  }

  it("finds instances of a non-repeatable varfield") {
    val varFields = List(
      createVarFieldWith(marcTag = "0", content = Some("Field 0A")),
      createVarFieldWith(marcTag = "1", content = Some("Field 1")),
      createVarFieldWith(marcTag = "0", content = Some("Field 0B")),
    )

    val bibData = createSierraBibDataWith(varFields = varFields)

    bibData.nonrepeatableVarfieldWithTag(tag = "1") shouldBe Some(varFields(1))
    bibData.nonrepeatableVarfieldWithTag(tag = "2") shouldBe None

    bibData.nonrepeatableVarfieldWithTag(tag = "0") shouldBe Some(varFields(0))
  }

  it("finds non-repeatable subfields") {
    val varField = createVarFieldWith(
      marcTag = "0",
      subfields = List(
        MarcSubfield(tag = "a", content = "Ablative armadillos"),
        MarcSubfield(tag = "b", content = "Brave butterflies"),
        MarcSubfield(tag = "b", content = "Billowing bison"),
      )
    )

    varField.nonrepeatableSubfieldWithTag(tag = "a") shouldBe Some(
      MarcSubfield(tag = "a", content = "Ablative armadillos")
    )

    varField.nonrepeatableSubfieldWithTag(tag = "b") shouldBe Some(
      MarcSubfield(tag = "b", content = "Brave butterflies Billowing bison")
    )

    varField.nonrepeatableSubfieldWithTag(tag = "c") shouldBe None
  }
}
