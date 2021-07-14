package weco.pipeline.transformer.tei

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.languages.Language
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
      TeiData(
        id = id,
        title = "test title",
        bNumber = None,
        description = Some(description),
        languages = Nil))
  }
  it("add the title from a tei into TeiData") {
    val titleString = "MS_345"
    TeiDataParser.parse(
      TeiXml(
        id,
        teiXml(id = id, title = titleElem(titleString))
          .toString()).right.get) shouldBe Right(
      TeiData(
        id = id,
        title = titleString,
        bNumber = None,
        description = None,
        languages = Nil))
  }
  it("add the languages from a tei into the WorkData") {
    val languageId = "sa"
    val languageLabel = "Sanskrit"
    val expectedTeiData = TeiData(
      id = id,
      title = "test title",
      bNumber = None,
      description = None,
      languages = List(Language(languageId, languageLabel)))
    TeiDataParser.parse(TeiXml(
      id,
      teiXml(id = id, languages = List(mainLanguage(languageId, languageLabel)))
        .toString()).right.get) shouldBe Right(expectedTeiData)
  }
  it("strips xml from descriptions TeiData") {
    val description = "a <note>manuscript</note> about stuff"
    TeiDataParser.parse(
      TeiXml(
        id,
        teiXml(id = id, summary = Some(summary(description)))
          .toString()).right.get) shouldBe Right(
      TeiData(
        id = id,
        title = "test title",
        bNumber = None,
        description = Some("a manuscript about stuff"),
        languages = Nil))
  }
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
