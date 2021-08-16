package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.Format.ArchivesAndManuscripts
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.data.{SierraBibData, SierraItemData}
import weco.sierra.models.fields.SierraMaterialType
import weco.sierra.models.marc.{Subfield, VarField}

class SierraShelfmarkTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators {
  it("returns no shelfmark if there is no 949") {
    val varFields = List()

    val itemData = createSierraItemDataWith(varFields = varFields)

    getShelfmark(itemData = itemData) shouldBe None
  }

  it("uses the contents of field 949 field tag c subfield ǂa") {
    val varFields = List(
      VarField(
        marcTag = Some("949"),
        fieldTag = Some("c"),
        subfields = List(
          Subfield(tag = "a", content = "S7956")
        )
      )
    )

    val itemData = createSierraItemDataWith(varFields = varFields)

    getShelfmark(itemData = itemData) shouldBe Some("S7956")
  }

  it("strips whitespace from shelfmarks") {
    val varFields = List(
      VarField(
        marcTag = Some("949"),
        fieldTag = Some("c"),
        subfields = List(
          Subfield(tag = "a", content = "/LEATHER            ")
        )
      )
    )

    val itemData = createSierraItemDataWith(varFields = varFields)

    getShelfmark(itemData = itemData) shouldBe Some("/LEATHER")
  }

  it("suppresses shelfmarks for Archives & Manuscripts") {
    val varFields = List(
      VarField(
        marcTag = Some("949"),
        fieldTag = Some("c"),
        subfields = List(
          Subfield(tag = "a", content = "PP/BOW/P.1.2.3/10:Box 123,1")
        )
      )
    )

    val bibData = createSierraBibData
    val itemData = createSierraItemDataWith(varFields = varFields)

    getShelfmark(bibData, itemData) shouldBe Some("PP/BOW/P.1.2.3/10:Box 123,1")

    getShelfmark(
      bibData.copy(
        materialType = Some(SierraMaterialType(ArchivesAndManuscripts.id))),
      itemData) shouldBe None
  }

  it("ignores any other 949 subfields") {
    val varFields = List(
      VarField(
        marcTag = Some("949"),
        fieldTag = Some("c"),
        subfields = List(
          Subfield(tag = "d", content = "X42461")
        )
      )
    )

    val itemData = createSierraItemDataWith(varFields = varFields)

    getShelfmark(itemData = itemData) shouldBe None
  }

  it("ignores any instances of 949 with a different field tag") {
    val varFields = List(
      VarField(
        marcTag = Some("949"),
        fieldTag = Some("a"),
        subfields = List(
          Subfield(tag = "a", content = "X42461")
        )
      )
    )

    val itemData = createSierraItemDataWith(varFields = varFields)

    getShelfmark(itemData = itemData) shouldBe None
  }

  it("suppresses the shelfmark if the bib has an iconographic number") {
    def getShelfmarkWith001(s: String): Option[String] = {
      val bibData = createSierraBibDataWith(
        materialType = Some(SierraMaterialType("k")),
        varFields = List(
          VarField(marcTag = Some("001"), content = Some(s))
        )
      )

      val varFields = List(
        VarField(
          marcTag = Some("949"),
          fieldTag = Some("c"),
          subfields = List(
            Subfield(tag = "a", content = "S7956")
          )
        )
      )

      val itemData = createSierraItemDataWith(varFields = varFields)

      getShelfmark(bibData = bibData, itemData = itemData)
    }

    getShelfmarkWith001(s = "3") shouldBe Some("S7956")
    getShelfmarkWith001(s = "12345i") shouldBe None
  }

  it(
    "shows the shelfmark if the iconographic number on the bib and item have a common prefix") {
    val bibData = createSierraBibDataWith(
      materialType = Some(SierraMaterialType("k")),
      varFields = List(
        VarField(marcTag = Some("001"), content = Some("12345i"))
      )
    )

    val varFields = List(
      VarField(
        marcTag = Some("949"),
        fieldTag = Some("c"),
        subfields = List(
          Subfield(tag = "a", content = "12345i.1")
        )
      )
    )

    val itemData = createSierraItemDataWith(varFields = varFields)

    getShelfmark(bibData = bibData, itemData = itemData) shouldBe Some(
      "12345i.1")
  }

  it(
    "skips the shelfmark if the iconographic number on the bib and item are the same") {
    val bibData = createSierraBibDataWith(
      materialType = Some(SierraMaterialType("k")),
      varFields = List(
        VarField(marcTag = Some("001"), content = Some("12345i"))
      )
    )

    val varFields = List(
      VarField(
        marcTag = Some("949"),
        fieldTag = Some("c"),
        subfields = List(
          Subfield(tag = "a", content = "12345i")
        )
      )
    )

    val itemData = createSierraItemDataWith(varFields = varFields)

    getShelfmark(bibData = bibData, itemData = itemData) shouldBe None
  }

  private def getShelfmark(
    bibData: SierraBibData = createSierraBibData,
    itemData: SierraItemData
  ): Option[String] =
    SierraShelfmark(bibData, itemData)
}
