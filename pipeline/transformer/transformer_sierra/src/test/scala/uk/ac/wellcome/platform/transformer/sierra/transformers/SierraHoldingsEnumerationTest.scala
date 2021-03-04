package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.platform.transformer.sierra.generators.MarcGenerators
import uk.ac.wellcome.platform.transformer.sierra.source.VarField
import weco.catalogue.sierra_adapter.generators.SierraGenerators

class SierraHoldingsEnumerationTest extends AnyFunSpec with Matchers with MarcGenerators with SierraGenerators {
  it("returns an empty list if there are no varFields with 853/863") {
    val varFields = List(
      createVarFieldWith(marcTag = "866"),
      createVarFieldWith(marcTag = "989"),
    )

    getEnumerations(varFields) shouldBe List()
  }

  def getEnumerations(varFields: List[VarField]): List[String] =
    SierraHoldingsEnumeration(createSierraHoldingsNumber, varFields)
}
