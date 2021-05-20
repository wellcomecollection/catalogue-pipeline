package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.platform.transformer.sierra.generators.{MarcGenerators, SierraDataGenerators}
import uk.ac.wellcome.platform.transformer.sierra.source.{MarcSubfield, VarField}
import weco.catalogue.internal_model.locations.AccessCondition

class SierraAccessConditionsTest extends AnyFunSpec with Matchers with MarcGenerators with SierraDataGenerators {
  it("drops an empty string in 506 subfield ǂa") {
    val accessConditions = getAccessConditions(
      bibVarFields = List(
        VarField(
          marcTag = Some("506"),
          subfields = List(
            MarcSubfield(tag = "a", content = "")
          )
        )
      )
    )

    accessConditions shouldBe empty
  }
  
  private def getAccessConditions(bibVarFields: List[VarField]): List[AccessCondition] =
    SierraAccessConditions(
      bibId = createSierraBibNumber,
      bibData = createSierraBibDataWith(varFields = bibVarFields)
    )
}
