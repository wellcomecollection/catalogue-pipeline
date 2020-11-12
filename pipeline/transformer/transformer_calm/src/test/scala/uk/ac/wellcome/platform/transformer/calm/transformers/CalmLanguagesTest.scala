package uk.ac.wellcome.platform.transformer.calm.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import uk.ac.wellcome.models.work.internal.Language

class CalmLanguagesTest extends AnyFunSpec with Matchers with TableDrivenPropertyChecks {
  val testCases = Table(
    ("languagesField", "expectedLanguages", "expectedLanguageNote"),

    // Degenerate cases: nothing in the Language field
    (None, List.empty, None),
    (Some(""), List.empty, None),

    // Cases where the contents of the Language field exactly matches a
    // language in the MARC Language list.
    (Some("English"), List(Language(label = "English", id = "eng")), None),
    (Some("Swedish"), List(Language(label = "Swedish", id = "swe")), None),
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
