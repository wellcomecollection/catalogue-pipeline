package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.platform.transformer.sierra.generators.MarcGenerators
import uk.ac.wellcome.sierra_adapter.model.SierraGenerators

class SierraElectronicResourcesTest extends AnyFunSpec with Matchers with MarcGenerators with SierraGenerators {
  it("doesn't find any items if there are no instances of field 856") {
    SierraElectronicResources(
      bibId = createSierraBibNumber,
      varFields = List()
    ) shouldBe empty

    SierraElectronicResources(
      bibId = createSierraBibNumber,
      varFields = List(
        createVarFieldWith(marcTag = "855"),
        createVarFieldWith(marcTag = "857")
      )
    ) shouldBe empty
  }
}
