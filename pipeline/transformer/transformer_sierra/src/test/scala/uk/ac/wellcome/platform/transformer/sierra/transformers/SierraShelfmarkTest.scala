package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.platform.transformer.sierra.generators.{
  MarcGenerators,
  SierraDataGenerators
}
import uk.ac.wellcome.platform.transformer.sierra.source.{
  MarcSubfield,
  SierraBibData,
  SierraItemData,
  SierraMaterialType
}
import weco.catalogue.internal_model.work.Format.ArchivesAndManuscripts

class SierraShelfmarkTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators {
  it("returns no shelfmark if there is no 949") {
    val varFields = List()

    val itemData = createSierraItemDataWith(varFields = varFields)

    getShelfmark(itemData = itemData) shouldBe None
  }

  it("uses the contents of field 949 subfield ǂa") {
    val varFields = List(
      createVarFieldWith(
        marcTag = "949",
        subfields = List(
          MarcSubfield(tag = "a", content = "S7956")
        )
      )
    )

    val itemData = createSierraItemDataWith(varFields = varFields)

    getShelfmark(itemData = itemData) shouldBe Some("S7956")
  }

  it("strips whitespace from shelfmarks") {
    val varFields = List(
      createVarFieldWith(
        marcTag = "949",
        subfields = List(
          MarcSubfield(tag = "a", content = "/LEATHER            ")
        )
      )
    )

    val itemData = createSierraItemDataWith(varFields = varFields)

    getShelfmark(itemData = itemData) shouldBe Some("/LEATHER")
  }

  it("suppresses shelfmarks for Archives & Manuscripts") {
    val varFields = List(
      createVarFieldWith(
        marcTag = "949",
        subfields = List(
          MarcSubfield(tag = "a", content = "PP/BOW/P.1.2.3/10:Box 123,1")
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
      createVarFieldWith(
        marcTag = "949",
        subfields = List(
          MarcSubfield(tag = "d", content = "X42461")
        )
      )
    )

    val itemData = createSierraItemDataWith(varFields = varFields)

    getShelfmark(itemData = itemData) shouldBe None
  }

  private def getShelfmark(
    bibData: SierraBibData = createSierraBibData,
    itemData: SierraItemData
  ): Option[String] =
    SierraShelfmark(bibData, itemData)
}
