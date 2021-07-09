package weco.pipeline.transformer.identifiers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import SourceIdentifierValidation._
import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}

class SourceIdentifierValidationTest extends AnyFunSpec with Matchers {
  def sourceIdentifier(
    identifierType: IdentifierType
  )(value: String): SourceIdentifier =
    SourceIdentifier(
      identifierType = identifierType,
      ontologyType = "Work",
      value = value
    )

  it("validates Sierra system numbers") {
    def sierraSystemNumber =
      sourceIdentifier(IdentifierType.SierraSystemNumber) _

    sierraSystemNumber("b12345679").validated should not be empty
    sierraSystemNumber("b1234567x").validated should not be empty
    sierraSystemNumber("1234567").validated shouldBe empty
  }

  it("validates Sierra identifiers") {
    def sierraIdentifier =
      sourceIdentifier(IdentifierType.SierraIdentifier) _

    sierraIdentifier("1234567").validated should not be empty
    sierraIdentifier("b12345679").validated shouldBe empty
    sierraIdentifier("123abcd").validated shouldBe empty
    sierraIdentifier("12345678").validated shouldBe empty
  }

  it("validates Miro image numbers") {
    def miroImageNumber = sourceIdentifier(IdentifierType.MiroImageNumber) _

    miroImageNumber("L0046161").validated should not be empty
    miroImageNumber("V0033167F1").validated should not be empty
    miroImageNumber("V0032544ECL").validated should not be empty
    miroImageNumber("L0003").validated shouldBe empty
    miroImageNumber("V0032544BLAHBLAH").validated shouldBe empty
  }

  it("validates iconographic numbers") {
    def iconographicNumber =
      sourceIdentifier(IdentifierType.IconographicNumber) _

    iconographicNumber("1234i").validated should not be empty
    iconographicNumber("123456i").validated should not be empty
    iconographicNumber("1234567").validated shouldBe empty
  }

  it("validates Wellcome digcodes") {
    def wellcomeDigcode = sourceIdentifier(IdentifierType.WellcomeDigcode) _

    wellcomeDigcode("digaids").validated should not be empty
    wellcomeDigcode("dig").validated shouldBe empty
    wellcomeDigcode("dibaids").validated shouldBe empty
  }

  it("validates CALM RefNos") {
    def calmRefNo = sourceIdentifier(IdentifierType.CalmRefNo) _

    calmRefNo("A/B/C").validated should not be empty
    calmRefNo("ABC").validated should not be empty
    calmRefNo("1/2").validated should not be empty
    calmRefNo("A/B()/%").validated shouldBe empty
  }
}
