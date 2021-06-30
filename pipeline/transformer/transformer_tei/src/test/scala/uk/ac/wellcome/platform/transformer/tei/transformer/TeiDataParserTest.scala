package uk.ac.wellcome.platform.transformer.tei.transformer

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.platform.transformer.tei.transformer.fixtures.TeiGenerators

class TeiDataParserTest extends AnyFunSpec with Matchers with TeiGenerators{
  val id = "manuscript_15651"

  it("parses a tei xml and returns TeiData"){
    val description = "a manuscript about stuff"
    TeiDataParser.parse(TeiXml(id, xmlString(id = id, summary = Some(summary(description))).toString()).right.get) shouldBe Right(TeiData(id, Some(description), None))
  }
  it("parses a tei xml and returns TeiData with bNumber"){
  val bnumber = "b1234567"
    TeiDataParser.parse(TeiXml(id, xmlString(id = id, identifiers = Some(sierraIdentifiers(bnumber))).toString()).right.get) shouldBe Right(TeiData(id, None, Some(bnumber)))
  }
}
