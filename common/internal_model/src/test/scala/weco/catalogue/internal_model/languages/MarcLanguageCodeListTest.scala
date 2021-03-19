package weco.catalogue.internal_model.languages

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class MarcLanguageCodeListTest extends AnyFunSpec with Matchers {
  it("returns None for a non-existent code") {
    MarcLanguageCodeList.lookupNameForCode(code = "doesnotexist") shouldBe None
  }

  it("finds a language by code") {
    MarcLanguageCodeList.lookupNameForCode(code = "abk") shouldBe Some("Abkhaz")
  }

  it("returns None for a name which isn't in the list") {
    MarcLanguageCodeList.lookupCodeForName(name = "Klingon") shouldBe None
  }

  it("finds a language by name") {
    MarcLanguageCodeList.lookupCodeForName(name = "German") shouldBe Some("ger")
  }

  it("finds a language by variant name") {
    MarcLanguageCodeList.lookupCodeForName(name = "Flemish") shouldBe Some(
      "dut")
  }

  it("finds a language with ambiguous names") {
    MarcLanguageCodeList.lookupCodeForName(name = "Inuit") shouldBe Some("iku")
  }

  it("handles obsolete codes") {
    MarcLanguageCodeList.lookupNameForCode(code = "tgl") shouldBe Some(
      "Tagalog")
    MarcLanguageCodeList.lookupNameForCode(code = "tag") shouldBe Some(
      "Tagalog")

    MarcLanguageCodeList.lookupCodeForName(name = "Tagalog") shouldBe Some(
      "tgl")
  }
}
