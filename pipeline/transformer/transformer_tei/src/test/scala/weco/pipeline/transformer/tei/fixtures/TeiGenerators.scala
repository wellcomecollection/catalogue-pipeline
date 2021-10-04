package weco.pipeline.transformer.tei.fixtures

import org.scalatest.Suite
import weco.fixtures.RandomGenerators

import scala.xml.{Elem, NodeSeq}

trait TeiGenerators extends RandomGenerators { this: Suite =>
  def sierraIdentifiers(bnumber: String) =
    <altIdentifier type="Sierra">
        <idno>{bnumber} </idno>
      </altIdentifier>

  def summary(str: String) = <summary>{str}</summary>

  def titleElem(str: String) =
    <publicationStmt><idno type="msID">{str}</idno></publicationStmt>

  def itemTitle(str: String) = <title>{str}</title>
  def originalItemTitle(str: String) = <title type="original">{str}</title>

  def mainLanguage(id: String, label: String) =
    <textLang mainLang={id} source="IANA">{label}</textLang>
  def otherLanguage(id: String, label: String) =
    <textLang otherLangs={id} source="IANA">{label}</textLang>

  def msItem(id: String,
             titles: List[Elem] = Nil,
             languages: List[Elem] = Nil) =
    <msItem xml:id={id}>
      {titles}
      {languages}
    </msItem>

  def msContents(summary: Option[Elem] = None,
                 languages: List[Elem] = Nil,
                 items: List[Elem] = Nil) =
    <msContents>
      {summary.getOrElse(NodeSeq.Empty)}
      {languages}
      {items}
    </msContents>

  def msPart(id: String,
             number: Int,
             summary: Option[Elem] = None,
             languages: List[Elem] = Nil) =
    <msPart xml:id={id} n={number.toString}>
      {msContents(summary = summary, languages = languages)}
    </msPart>

  def teiXml(
    id: String = randomAlphanumeric(),
    title: Elem = titleElem("test title"),
    identifiers: Option[Elem] = None,
    summary: Option[Elem] = None,
    languages: List[Elem] = Nil,
    items: List[Elem] = Nil,
    parts: List[Elem] = Nil
  ): Elem =
    <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id={id}>
      <teiHeader>
        <fileDesc>
        {title}
          <sourceDesc>
            <msDesc xml:lang="en" xml:id="MS_Arabic_1">
              <msIdentifier>
              {identifiers.getOrElse(NodeSeq.Empty)}
              </msIdentifier>
              {msContents(summary, languages, items)}
              {parts}
            </msDesc>
          </sourceDesc>
        </fileDesc>
      </teiHeader>
    </TEI>
}
