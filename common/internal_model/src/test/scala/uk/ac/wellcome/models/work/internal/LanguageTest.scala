package uk.ac.wellcome.models.work.internal

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class LanguageTest extends AnyFunSpec with Matchers {
  it("gets labels for known code or errors") {
    val withValidCode = Language.fromCode("yo")
    val withInvalidCode = Language.fromCode("no such code")

    withValidCode should be(Right(Language("Yoruba", Some("yo"))))
    withInvalidCode shouldBe a[Left[_, _]]
  }

  it(
    "gets Language with code from known labels and omits code for unknown labels") {
    val withValidLabel = Language.fromLabel("Yoruba")
    val withMultiLabel1 = Language.fromLabel("Haitian")
    val withMultiLabel2 = Language.fromLabel("Haitian Creole")
    val withInvalidLabel = Language.fromLabel("no such label")

    withValidLabel shouldBe Right(Language("Yoruba", Some("yo")))
    withMultiLabel1 shouldBe Right(Language("Haitian", Some("ht")))
    withMultiLabel2 shouldBe Right(Language("Haitian Creole", Some("ht")))
    withInvalidLabel shouldBe Right(Language("no such label", None))
  }
}
