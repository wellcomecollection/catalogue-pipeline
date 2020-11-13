package uk.ac.wellcome.models.marc

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal.Language

class MarcLanguageCodeListTest extends AnyFunSpec with Matchers {
  it("returns None for a non-existent code") {
    MarcLanguageCodeList.lookupByCode(code = "doesnotexist") shouldBe None
  }

  it("finds a language by code") {
    MarcLanguageCodeList.lookupByCode(code = "abk") shouldBe Some(
      Language(label = "Abkhaz", id = "abk"))
  }

  it("returns None for a name which isn't in the list") {
    MarcLanguageCodeList.lookupByName(name = "Klingon") shouldBe None
  }

  it("finds a language by name") {
    MarcLanguageCodeList.lookupByName(name = "German") shouldBe Some(
      Language(label = "German", id = "ger"))
  }

  it("finds a language by variant name") {
    MarcLanguageCodeList.lookupByName(name = "Flemish") shouldBe Some(
      Language(label = "Flemish", id = "dut"))
  }

  it("finds a language with ambiguous names") {
    MarcLanguageCodeList.lookupByName(name = "Inuit") shouldBe Some(
      Language(label = "Inuit", id = "iku"))
  }

  it("handles obsolete codes") {
    MarcLanguageCodeList.lookupByCode(code = "tgl") shouldBe Some(
      Language(label = "Tagalog", id = "tgl"))
    MarcLanguageCodeList.lookupByCode(code = "tag") shouldBe Some(
      Language(label = "Tagalog", id = "tag"))

    MarcLanguageCodeList.lookupByName(name = "Tagalog") shouldBe Some(
      Language(label = "Tagalog", id = "tgl"))
  }
}
