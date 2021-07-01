package weco.pipeline.transformer.tei

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.source_model.generators.SierraDataGenerators
import weco.pipeline.transformer.tei.fixtures.TeiGenerators

class TeiDataParserTest
    extends AnyFunSpec
    with Matchers
    with TeiGenerators
    with SierraDataGenerators {
  val id = "manuscript_15651"
  val bnumber = createSierraBibNumber.withCheckDigit
  it("parses a tei xml and returns TeiData") {
    val description = "a manuscript about stuff"
    TeiDataParser.parse(
      TeiXml(
        id,
        teiXml(id = id, summary = Some(summary(description)))
          .toString()).right.get) shouldBe Right(
      TeiData(id, Some(description), None))
  }
  it("parses a tei xml and returns TeiData with bNumber") {

    TeiDataParser.parse(
      TeiXml(
        id,
        teiXml(id = id, identifiers = Some(sierraIdentifiers(bnumber)))
          .toString()).right.get) shouldBe Right(
      TeiData(id, None, Some(bnumber)))
  }
  it("fails parsing if there's more than one bnumber node") {

    val xml = <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id={id}>
      <teiHeader>
        <fileDesc>
          <sourceDesc>
            <msDesc xml:lang="en" xml:id="MS_Arabic_1">
              <msIdentifier>
                {sierraIdentifiers(bnumber)}
                {sierraIdentifiers(bnumber)}
              </msIdentifier>
              <msContents>
              </msContents>
            </msDesc>
          </sourceDesc>
        </fileDesc>
      </teiHeader>
    </TEI>
    val result = TeiDataParser.parse(TeiXml(id, xml.toString()).right.get)
    result shouldBe a[Left[_, _]]
    result.left.get shouldBe a[RuntimeException]
  }
  it("fails parsing if there's more than one summary node") {

    val xml = <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id={id}>
      <teiHeader>
        <fileDesc>
          <sourceDesc>
            <msDesc xml:lang="en" xml:id="MS_Arabic_1">
              <msContents>
                {summary(bnumber)}
                {summary(bnumber)}
              </msContents>
            </msDesc>
          </sourceDesc>
        </fileDesc>
      </teiHeader>
    </TEI>
    val result = TeiDataParser.parse(TeiXml(id, xml.toString()).right.get)
    result shouldBe a[Left[_, _]]
    result.left.get shouldBe a[RuntimeException]
  }
}
