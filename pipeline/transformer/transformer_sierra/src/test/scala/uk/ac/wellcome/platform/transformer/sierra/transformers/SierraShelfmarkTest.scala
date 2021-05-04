package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.platform.transformer.sierra.generators.{
  MarcGenerators,
  SierraDataGenerators
}
import uk.ac.wellcome.platform.transformer.sierra.source.MarcSubfield

class SierraShelfmarkTest extends AnyFunSpec with Matchers with MarcGenerators with SierraDataGenerators {
  it("returns no shelfmark if there is no 949") {
    val varFields = List()

    val itemData = createSierraItemDataWith(varFields = varFields)

    SierraShelfmark(itemData) shouldBe None
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

    SierraShelfmark(itemData) shouldBe Some("S7956")
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

    SierraShelfmark(itemData) shouldBe None
  }
}
