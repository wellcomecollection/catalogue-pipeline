package uk.ac.wellcome.models.work.internal

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

class LanguageTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with TableDrivenPropertyChecks {
  it("gets the language from a known code") {
    Language.fromCode("yo").right.value shouldBe Language(
      label = "Yoruba",
      id = "yo")
  }

  it("errors if asked to retrieve an unknown code") {
    Language.fromCode("no such code").left.value shouldBe a[Exception]
  }

  val labelTestCases = Table(
    ("label", "expectedLanguage"),
    ("Yoruba", Language(label = "Yoruba", id = "yo")),
    ("Haitian", Language(label = "Haitian", id = "ht")),
    ("Haitian Creole", Language(label = "Haitian Creole", id = "ht")),
    ("no such label", Language(label = "no such label", id = None)),
  )

  it("gets the language from a label") {
    forAll(labelTestCases) {
      case (label, expectedLanguage) =>
        Language.fromLabel(label).right.value shouldBe expectedLanguage
    }
  }
}
