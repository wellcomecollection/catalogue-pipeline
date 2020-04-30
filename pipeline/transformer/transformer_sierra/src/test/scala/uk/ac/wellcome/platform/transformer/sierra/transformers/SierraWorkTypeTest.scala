package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal.WorkType.Books
import uk.ac.wellcome.platform.transformer.sierra.generators.SierraDataGenerators
import uk.ac.wellcome.platform.transformer.sierra.source.SierraMaterialType

class SierraWorkTypeTest
    extends AnyFunSpec
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
