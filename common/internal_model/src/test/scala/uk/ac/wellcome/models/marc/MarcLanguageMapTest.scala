package uk.ac.wellcome.models.marc

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class MarcLanguageMapTest extends AnyFunSpec with Matchers {
  it("returns None for a non-existent code") {
    MarcLanguageMap.lookupById(code = "doesnotexist") shouldBe None
  }

  it("finds a language with a single name") {
    MarcLanguageMap.lookupById(code = "abk") shouldBe Some("Abkhaz")
  }
}
