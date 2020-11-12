package uk.ac.wellcome.platform.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal.Language
import uk.ac.wellcome.platform.transformer.sierra.generators.SierraDataGenerators
import uk.ac.wellcome.platform.transformer.sierra.source.sierra.SierraSourceLanguage

class SierraLanguagesTest extends AnyFunSpec with Matchers with SierraDataGenerators {
  it("ignores records without any languages") {
    val bibData = createSierraBibDataWith(lang = None, varFields = List.empty)

    SierraLanguages(bibData) shouldBe empty
  }

  it("parses a single language from the 'lang' field") {
    val bibData = createSierraBibDataWith(
      lang = Some(
        SierraSourceLanguage(code = "fre", name = "French")
      ),
      varFields = List.empty
    )

    SierraLanguages(bibData) shouldBe List(Language(label = "French", id = "fre"))
  }
}
