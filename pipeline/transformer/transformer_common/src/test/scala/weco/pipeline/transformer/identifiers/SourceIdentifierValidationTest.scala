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

    sierraSystemNumber("b12345679").validate should not be empty
    sierraSystemNumber("b1234567x").validate should not be empty
    sierraSystemNumber("1234567").validate shouldBe empty
  }

  it("validates Sierra identifiers") {
    def sierraIdentifier =
      sourceIdentifier(IdentifierType.SierraIdentifier) _

    sierraIdentifier("1234567").validate should not be empty
    sierraIdentifier("b12345679").validate shouldBe empty
    sierraIdentifier("123abcd").validate shouldBe empty
    sierraIdentifier("12345678").validate shouldBe empty
  }

  it("validates Miro image numbers") {
    def miroImageNumber = sourceIdentifier(IdentifierType.MiroImageNumber) _

    miroImageNumber("L0046161").validate should not be empty
    miroImageNumber("V0033167F1").validate should not be empty
    miroImageNumber("V0032544ECL").validate should not be empty
    miroImageNumber("L0003").validate shouldBe empty
    miroImageNumber("V0032544BLAHBLAH").validate shouldBe empty
  }

  it("validates iconographic numbers") {
    def iconographicNumber =
      sourceIdentifier(IdentifierType.IconographicNumber) _

    iconographicNumber("1234i").validate should not be empty
    iconographicNumber("123456i").validate should not be empty
    iconographicNumber("1234567").validate shouldBe empty
  }

  it("validates Wellcome digcodes") {
    def wellcomeDigcode = sourceIdentifier(IdentifierType.WellcomeDigcode) _

    wellcomeDigcode("digaids").validate should not be empty
    wellcomeDigcode("dig").validate shouldBe empty
    wellcomeDigcode("dibaids").validate shouldBe empty
  }

  it("validates CALM RefNos") {
    def calmRefNo = sourceIdentifier(IdentifierType.CalmRefNo) _

    calmRefNo("A/B/C").validate should not be empty
    calmRefNo("ABC").validate should not be empty
    calmRefNo("1/2").validate should not be empty
    calmRefNo("A/B()/%").validate shouldBe empty
  }
}
