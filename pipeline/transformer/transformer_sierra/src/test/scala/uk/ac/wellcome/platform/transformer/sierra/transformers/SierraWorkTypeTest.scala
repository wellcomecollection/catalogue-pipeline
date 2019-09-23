package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.internal.WorkType
import uk.ac.wellcome.platform.transformer.sierra.source.SierraMaterialType
import uk.ac.wellcome.platform.transformer.sierra.generators.SierraDataGenerators

class SierraWorkTypeTest
    extends FunSpec
    with Matchers
    with SierraDataGenerators {

  it("extracts WorkType from bib records") {
    val workTypeId = "a"
    val sierraValue = "Books"
    val bibId = createSierraBibNumber

    val bibData = createSierraBibDataWith(
      materialType = Some(
        SierraMaterialType(code = workTypeId)
      )
    )

    val expectedWorkType = WorkType(
      id = workTypeId,
      label = sierraValue
    )

    SierraWorkType(bibId, bibData) shouldBe Some(expectedWorkType)
  }
}
