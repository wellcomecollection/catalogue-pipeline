package weco.pipeline.transformer.tei.data

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.languages.Language

class TeiLanguageDataTest
    extends AnyFunSpec
    with Matchers
    with TableDrivenPropertyChecks {
  val testCases = Table(
    ("id", "label", "expectedLanguage"),
    ("ar", "Arabic", Language(id = "ara", label = "Arabic")),
    ("jv", "Javanese", Language(id = "jav", label = "Javanese")),
    (
      "grc",
      "Ancient Greek",
      Language(id = "grc", label = "Greek, Ancient (to 1453)")),
    ("btk", "Toba-Batak", Language(id = "btk", label = "Toba-Batak"))
  )

  it("handles the test cases") {
    forAll(testCases) {
      case (id, label, expectedLanguage) =>
        TeiLanguageData(id = id, label = label) shouldBe Right(expectedLanguage)
    }
  }

  it("fails if it sees an unexpected language") {
    TeiLanguageData(id = "xyz", label = "???") shouldBe a[Left[_, _]]
  }
}
