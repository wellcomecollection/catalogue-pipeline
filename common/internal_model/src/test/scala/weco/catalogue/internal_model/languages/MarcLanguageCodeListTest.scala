package weco.catalogue.internal_model.languages

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class MarcLanguageCodeListTest extends AnyFunSpec with Matchers {
  it("returns None for a non-existent code") {
    MarcLanguageCodeList.fromCode(code = "doesnotexist") shouldBe None
  }

  it("finds a language by code") {
    MarcLanguageCodeList.fromCode(code = "abk") shouldBe Some(
      Language(label = "Abkhaz", id = "abk"))
  }

  it("returns None for a name which isn't in the list") {
    MarcLanguageCodeList.fromName(name = "Klingon") shouldBe None
  }

  it("finds a language by name") {
    MarcLanguageCodeList.fromName(name = "German") shouldBe Some(
      Language(label = "German", id = "ger"))
  }

  it("finds Arabic by name") {
    MarcLanguageCodeList.fromName(name = "Arabic") shouldBe Some(
      Language(id = "ara", label = "Arabic"))
  }

  it("finds a language by variant name") {
    MarcLanguageCodeList.fromName(name = "Flemish") shouldBe Some(
      Language(label = "Flemish", id = "dut"))
  }

  it("finds a language with ambiguous names") {
    MarcLanguageCodeList.fromName(name = "Inuit") shouldBe Some(
      Language(label = "Inuit", id = "iku"))
  }

  it("handles obsolete codes") {
    MarcLanguageCodeList.fromCode(code = "tgl") shouldBe Some(
      Language(label = "Tagalog", id = "tgl"))
    MarcLanguageCodeList.fromCode(code = "tag") shouldBe Some(
      Language(label = "Tagalog", id = "tag"))

    MarcLanguageCodeList.fromName(name = "Tagalog") shouldBe Some(
      Language(label = "Tagalog", id = "tgl"))
  }
}
