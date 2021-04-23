package uk.ac.wellcome.display.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.work.{LanguageNote, LetteringNote}

class DisplayNoteTest
    extends AnyFunSpec
    with Matchers
    with TableDrivenPropertyChecks {
  val testCases = Table(
    ("note", "displayNote"),
    (
      LetteringNote("Approximately 95%"),
      DisplayNote(
        contents = List("Approximately 95%"),
        noteType = DisplayNoteType("lettering-note", "Lettering note")
      )
    ),
    (
      LanguageNote("Open signed in American Sign language."),
      DisplayNote(
        contents = List("Open signed in American Sign language."),
        noteType = DisplayNoteType("language-note", "Language note")
      )
    ),
  )

  it("converts a Note to a DisplayNote") {
    forAll(testCases) {
      case (note, displayNote) =>
        DisplayNote(note) shouldBe displayNote
    }
  }
}
