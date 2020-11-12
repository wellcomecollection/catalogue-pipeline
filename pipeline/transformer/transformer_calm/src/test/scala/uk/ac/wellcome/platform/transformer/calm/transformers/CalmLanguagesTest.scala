package uk.ac.wellcome.platform.transformer.calm.transformers

import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor2}
import uk.ac.wellcome.models.work.internal.Language

class CalmLanguagesTest extends AnyFunSpec with Matchers with TableDrivenPropertyChecks {
  it("handles degenerate cases") {
    val degenerateTestCases =
      Table(
        "languagesField",
        None,
        Some(""),
        Some("\n\n"),
      )

    forAll(degenerateTestCases) { languagesField =>
      CalmLanguages(languagesField) shouldBe ((List.empty, None))
    }
  }

  // If the language field is an exact match for a language in the
  // MARC Language list, we just return that.
  val exactMatchTestCases = Table(
    ("languagesField", "expectedLanguages"),
    ("English", List(Language(label = "English", id = "eng"))),
    ("Swedish", List(Language(label = "Swedish", id = "swe"))),
  )

  it("handles exact matches") {
    runTestCases(exactMatchTestCases)
  }

  // If the language field is a combination of exact matches for languages
  // in the MARC Language list, we just return those.
  val multiMatchTestCases = Table(
    ("languagesField", "expectedLanguages"),
    (
      "Portuguese\nSpanish",
      List(
        Language(label = "Portuguese", id = "por"),
        Language(label = "Spanish", id = "spa")
      )
    ),
    ("English.", List(Language(label = "English", id = "eng"))),
    ("English`", List(Language(label = "English", id = "eng"))),
    (
      "German; French",
      List(
        Language(label = "German", id = "ger"),
        Language(label = "French", id = "fre")
      )
    ),
    (
      "English, Chinese",
      List(
        Language(label = "English", id = "eng"),
        Language(label = "Chinese", id = "chi")
      )
    ),
    (
      "English/French",
      List(
        Language(label = "English", id = "eng"),
        Language(label = "French", id = "fre")
      )
    ),
    // We have to be a little bit careful splitting 'and' -- some languages
    // have "and" as part of the name, and we don't want to lose those.
    (
      "English/Ganda",
      List(
        Language(label = "English", id = "eng"),
        Language(label = "Ganda", id = "lug")
      )
    ),
    (
      "English and Russian",
      List(
        Language(label = "English", id = "eng"),
        Language(label = "Russian", id = "rus")
      )
    ),
  )

  it("handles multiple matches") {
    runTestCases(multiMatchTestCases)
  }

  // It handles some edge cases, all based on real Calm records that
  // aren't quite exact matches.
  val fuzzyTestCases = Table(
    ("languagesField", "expectedLanguages"),
    ("Portguese", List(Language(label = "Portuguese", id = "por"))),
    ("Swiss-German", List(Language(label = "Swiss German", id = "gsw"))),
    (
      "English and Norweigan",
      List(
        Language(label = "English", id = "eng"),
        Language(label = "Norwegian", id = "nor")
      )
    ),
    (
      "English, Portugese, French and Spanish",
      List(
        Language(label = "English", id = "eng"),
        Language(label = "Portuguese", id = "por"),
        Language(label = "French", id = "fre"),
        Language(label = "Spanish", id = "spa"),
      )
    ),
  )

  it("handles fuzzy cases") {
    runTestCases(fuzzyTestCases)
  }

  def runTestCases(testCases: TableFor2[String, List[Language]]): Assertion =
    forAll(testCases) {
      case (languagesField, expectedLanguages) =>
        val (languages, languageNote) = CalmLanguages(Some(languagesField))
        languages shouldBe expectedLanguages
        languageNote shouldBe None
    }
}
