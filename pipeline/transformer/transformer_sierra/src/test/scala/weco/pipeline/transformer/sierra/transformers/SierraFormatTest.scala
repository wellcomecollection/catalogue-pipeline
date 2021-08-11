package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.Format.Books
import weco.sierra.generators.SierraDataGenerators
import weco.sierra.models.fields.SierraMaterialType

class SierraFormatTest
    extends AnyFunSpec
    with Matchers
    with SierraDataGenerators {

  it("extracts Format from bib records") {
    val formatId = "a"

    val bibData = createSierraBibDataWith(
      materialType = Some(
        SierraMaterialType(code = formatId)
      )
    )

    val expectedFormat = Books

    SierraFormat(bibData) shouldBe Some(expectedFormat)
  }
}
