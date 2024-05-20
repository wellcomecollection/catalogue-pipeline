package weco.pipeline.transformer.calm.transformers

import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor2}
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.work.{Note, NoteType}

class CalmLanguagesTest
    extends AnyFunSpec
    with Matchers
    with TableDrivenPropertyChecks {
  it("handles degenerate cases") {
    val degenerateTestCases =
      Table(
        "languagesField",
        "",
        "  ",
        "\n\n"
      )

    forAll(degenerateTestCases) {
      value =>
        CalmLanguages(List(value)) shouldBe ((List(), List()))
    }
  }

  // If the language field is an exact match for a language in the
  // MARC Language list, we just return that.
  val exactMatchTestCases = Table(
    ("languagesField", "expectedLanguages"),
    ("English", List(Language(label = "English", id = "eng"))),
    ("Swedish", List(Language(label = "Swedish", id = "swe"))),
    // A variant name in the MARC Language list
    ("Mandarin", List(Language(label = "Mandarin", id = "chi"))),
    ("Middle English", List(Language(label = "Middle English", id = "enm")))
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
      "German, French, ",
      List(
        Language(label = "German", id = "ger"),
        Language(label = "French", id = "fre")
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
    )
  )

  it("handles multiple matches") {
    runTestCases(multiMatchTestCases)
  }

  val languageTagCases = Table(
    ("languagesField", "expectedLanguages"),
    (
      "<language>French</language>",
      List(Language(label = "French", id = "fre"))
    ),
    // Case with multiple languages with matching langcodes
    (
      "<language langcode=\"ger\">German, </language><language langcode=\"fre\">French, </language>",
      List(
        Language(label = "German", id = "ger"),
        Language(label = "French", id = "fre")
      )
    )
  )

  it("handles <language> tags in the Calm data") {
    runTestCases(languageTagCases)
  }

  // It handles some edge cases, all based on real Calm records that
  // aren't quite exact matches.
  val fuzzyTestCases = Table(
    ("languagesField", "expectedLanguages"),
    ("Portguese", List(Language(label = "Portuguese", id = "por"))),
    ("Potuguese", List(Language(label = "Portuguese", id = "por"))),
    ("Lugandan", List(Language(label = "Luganda", id = "lug"))),
    ("Swiss-German", List(Language(label = "Swiss German", id = "gsw"))),
    ("Eng", List(Language(label = "English", id = "eng"))),
    ("Language", List.empty),
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
        Language(label = "Spanish", id = "spa")
      )
    )
  )

  it("handles fuzzy cases") {
    runTestCases(fuzzyTestCases)
  }

  val fallbackTestCases = Table(
    ("languagesField", "expectedLanguages"),
    (
      "Partly in German, partly in English, some articles in French.",
      List(
        Language(label = "German", id = "ger"),
        Language(label = "English", id = "eng"),
        Language(label = "French", id = "fre")
      )
    ),
    ("Nigerian", List.empty)
  )

  it("handles fallback cases") {
    forAll(fallbackTestCases) {
      case (languageField, expectedLanguages) =>
        val (languages, languageNotes) = CalmLanguages(List(languageField))
        languages shouldBe expectedLanguages
        languageNotes shouldBe List(
          Note(contents = languageField, noteType = NoteType.LanguageNote)
        )
    }
  }

  it("fixes spelling errors") {
    // Taken from f9f09f42-675d-4d27-8efa-1726d314f20b
    // We can remove this test and the fixup code once the record is corrected.
    val (languages, languageNotes) = CalmLanguages(
      List(
        "The majority of this collection is in English, however Kitzinger recieved " +
          "letters from around the world and travelled widely for conferences so some " +
          "material is not."
      )
    )

    languages shouldBe List(Language(label = "English", id = "eng"))
    languageNotes shouldBe List(
      Note(
        contents =
          "The majority of this collection is in English, however Kitzinger received " +
            "letters from around the world and travelled widely for conferences so some " +
            "material is not.",
        noteType = NoteType.LanguageNote
      )
    )
  }

  it("combines languages and notes from multiple values") {
    val (languages, notes) = CalmLanguages(
      List(
        "English; German",
        "French with a Polish translation",
        "Dutch",
        "Chinese inscription"
      )
    )

    languages shouldBe List(
      Language(label = "English", id = "eng"),
      Language(label = "German", id = "ger"),
      Language(label = "French", id = "fre"),
      Language(label = "Polish", id = "pol"),
      Language(label = "Dutch", id = "dut"),
      Language(label = "Chinese", id = "chi")
    )

    notes shouldBe List(
      Note(
        contents = "French with a Polish translation",
        noteType = NoteType.LanguageNote
      ),
      Note(contents = "Chinese inscription", noteType = NoteType.LanguageNote)
    )
  }

  it("deduplicates languages and notes") {
    val (languages, notes) = CalmLanguages(
      List(
        "English; Chinese",
        "Chinese inscription",
        "Chinese inscription"
      )
    )

    languages shouldBe List(
      Language(label = "English", id = "eng"),
      Language(label = "Chinese", id = "chi")
    )

    notes shouldBe List(
      Note(contents = "Chinese inscription", noteType = NoteType.LanguageNote)
    )
  }

  def runTestCases(testCases: TableFor2[String, List[Language]]): Assertion =
    forAll(testCases) {
      case (languageField, expectedLanguages) =>
        val (languages, languageNote) = CalmLanguages(List(languageField))
        languages shouldBe expectedLanguages
        languageNote shouldBe List()
    }
}
