package uk.ac.wellcome.platform.transformer.sierra.source

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.platform.transformer.sierra.generators.{MarcGenerators, SierraDataGenerators}

class SierraQueryOpsTest
  extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with SierraDataGenerators
    with SierraQueryOps {

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
}
