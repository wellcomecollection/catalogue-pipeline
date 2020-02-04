package uk.ac.wellcome.platform.transformer.calm.transformers

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.internal.Language
import uk.ac.wellcome.platform.transformer.calm.source.sierra.SierraSourceLanguage
import uk.ac.wellcome.platform.transformer.calm.generators.SierraDataGenerators

class SierraLanguageTest
    extends FunSpec
    with Matchers
    with SierraDataGenerators {

  it("ignores records which don't have a lang field") {
    val bibData = createSierraBibDataWith(lang = None)
    SierraLanguage(createSierraBibNumber, bibData) shouldBe None
  }

  it("picks up the language from the lang field") {
    val bibData = createSierraBibDataWith(
      lang = Some(
        SierraSourceLanguage(
          code = "eng",
          name = "English"
        ))
    )

    SierraLanguage(createSierraBibNumber, bibData) shouldBe Some(
      Language(
        id = "eng",
        label = "English"
      ))
  }
}
