package uk.ac.wellcome.models.marc

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class MarcLanguageCodeListTest extends AnyFunSpec with Matchers {
  it("returns None for a non-existent code") {
    MarcLanguageCodeList.lookupByCode(code = "doesnotexist") shouldBe None
  }

  it("finds a language by code") {
    MarcLanguageCodeList.lookupByCode(code = "abk") shouldBe Some("Abkhaz")
  }

  it("returns None for a name which isn't in the list") {
    MarcLanguageCodeList.lookupByName(name = "Klingon") shouldBe None
  }

  it("finds a language by name") {
    MarcLanguageCodeList.lookupByName(name = "German") shouldBe Some("ger")
  }

  it("handles obsolete codes") {
    MarcLanguageCodeList.lookupByCode(code = "tgl") shouldBe Some("Tagalog")
    MarcLanguageCodeList.lookupByCode(code = "tag") shouldBe Some("Tagalog")

    MarcLanguageCodeList.lookupByName(name = "Tagalog") shouldBe Some("tgl")
  }
}
