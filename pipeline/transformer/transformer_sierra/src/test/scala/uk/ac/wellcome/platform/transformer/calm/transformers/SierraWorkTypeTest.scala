package uk.ac.wellcome.platform.transformer.calm.transformers

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.internal.WorkType.Books
import uk.ac.wellcome.platform.transformer.calm.generators.SierraDataGenerators
import uk.ac.wellcome.platform.transformer.calm.source.SierraMaterialType

class SierraWorkTypeTest
    extends FunSpec
    with Matchers
    with SierraDataGenerators {

  it("extracts WorkType from bib records") {
    val workTypeId = "a"
    val bibId = createSierraBibNumber

    val bibData = createSierraBibDataWith(
      materialType = Some(
        SierraMaterialType(code = workTypeId)
      )
    )

    val expectedWorkType = Books

    SierraWorkType(bibId, bibData) shouldBe Some(expectedWorkType)
  }
}
