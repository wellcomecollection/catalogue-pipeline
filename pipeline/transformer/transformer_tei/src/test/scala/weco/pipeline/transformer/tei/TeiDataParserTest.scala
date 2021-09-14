package weco.pipeline.transformer.tei

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.languages.Language
import weco.pipeline.transformer.tei.fixtures.TeiGenerators
import weco.sierra.generators.SierraIdentifierGenerators

class TeiDataParserTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with TeiGenerators
    with SierraIdentifierGenerators {
  val id = "manuscript_15651"
  val bnumber = createSierraBibNumber.withCheckDigit

  it("parses a tei xml and returns TeiData with bNumber") {
    TeiDataParser.parse(
      TeiXml(
        id,
        teiXml(id = id, identifiers = Some(sierraIdentifiers(bnumber)))
          .toString()).right.get) shouldBe Right(
      TeiData(
        id = id,
        title = "test title",
        bNumber = Some(bnumber),
        description = None,
        languages = Nil))
  }
}
