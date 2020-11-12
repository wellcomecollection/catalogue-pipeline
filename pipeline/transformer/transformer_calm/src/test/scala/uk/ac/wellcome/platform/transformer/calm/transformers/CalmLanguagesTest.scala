package uk.ac.wellcome.platform.transformer.calm.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

class CalmLanguagesTest extends AnyFunSpec with Matchers with TableDrivenPropertyChecks {
  val testCases = Table(
    ("languagesField", "expectedLanguages", "expectedLanguageNote"),
    (None, Seq.empty, None),
    (Some(""), Seq.empty, None),
  )

  it("parses the Language field") {
    forAll(testCases) {
      case (languagesField, expectedLanguages, expectedLanguageNote) =>
        val (languages, languageNote) = CalmLanguages(languagesField)
        languages shouldBe expectedLanguages
        languageNote shouldBe expectedLanguageNote
    }
  }
}
