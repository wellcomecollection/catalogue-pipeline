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

  def runTestCases(testCases: TableFor2[String, List[Language]]): Assertion =
    forAll(testCases) {
      case (languagesField, expectedLanguages) =>
        val (languages, languageNote) = CalmLanguages(Some(languagesField))
        languages shouldBe expectedLanguages
        languageNote shouldBe None
    }
}
