package uk.ac.wellcome.models.marc

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class MarcLanguageCodeListTest extends AnyFunSpec with Matchers {
  it("returns None for a non-existent code") {
    MarcLanguageCodeList.lookupById(code = "doesnotexist") shouldBe None
  }

  it("finds a language with a single name") {
    MarcLanguageCodeList.lookupById(code = "abk") shouldBe Some("Abkhaz")
  }
}
