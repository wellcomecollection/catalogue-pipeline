package weco.pipeline.transformer.tei.generators

import org.scalatest.Suite
import weco.fixtures.RandomGenerators

import scala.xml.{Elem, NodeSeq}

trait TeiGenerators extends RandomGenerators { this: Suite =>
  def teiXml(
    id: String = randomAlphanumeric(),
    title: NodeSeq = titleElem("test title"),
    identifiers: Option[Elem] = None,
    summary: Option[Elem] = None,
    languages: List[Elem] = Nil,
    items: List[Elem] = Nil,
    parts: List[Elem] = Nil,
    catalogues: List[Elem] = Nil,
    authors: List[Elem] = Nil
  ): Elem =
    <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id={id}>
      <teiHeader>
        <fileDesc>
          <publicationStmt>
            {title}
            {catalogues}
          </publicationStmt>
          <sourceDesc>
            <msDesc xml:lang="en" xml:id="MS_Arabic_1">
              <msIdentifier>
                {identifiers.getOrElse(NodeSeq.Empty)}
              </msIdentifier>
              {msContents(summary, languages, items, authors)}
              {parts}
            </msDesc>
          </sourceDesc>
        </fileDesc>
      </teiHeader>
    </TEI>

  def msContents(
    summary: Option[Elem] = None,
    languages: List[Elem] = Nil,
    items: List[Elem] = Nil,
    authors: List[Elem] = Nil
  ) =
    <msContents>
      {summary.getOrElse(NodeSeq.Empty)}
      {languages}
      {items}
      {authors}
    </msContents>

  def msItem(
    id: String,
    titles: List[Elem] = Nil,
    languages: List[Elem] = Nil,
    items: List[Elem] = Nil,
    authors: List[Elem] = Nil
  ) =
    <msItem xml:id={id}>
      {titles}
      {languages}
      {items}
      {authors}
    </msItem>

  def msPart(
    id: String,
    summary: Option[Elem] = None,
    languages: List[Elem] = Nil,
    items: List[Elem] = Nil,
    authors: List[Elem] = Nil
  ) =
    <msPart xml:id={id}>
      {msContents(summary = summary, languages = languages, items = items, authors = authors)}
    </msPart>

  def sierraIdentifiers(bnumber: String) =
    <altIdentifier type="Sierra">
        <idno>{bnumber} </idno>
      </altIdentifier>

  def author(label:String, key: Option[String]=None) = key match {
    case Some(k) => <author key={k}>{label}</author>
    case None => <author>{label}</author>
  }

  def author(persNames: List[Elem], key: Option[String]) =
    key match {
      case Some(k) => <author key={k}>{persNames}</author>
      case None => <author>{persNames}</author>
    }

  def persName(label: String, key: Option[String]= None, `type`: Option[String] = None) =  (key, `type`) match {
    case (Some(k), Some(t)) => <persName key={k} type={t}>{label}</persName>
    case (Some(k), None) => <persName key={k}>{label}</persName>
    case (None, Some(t)) => <persName type={t}>{label}</persName>
    case (None, None) => <persName>{label}</persName>
  }

  def summary(str: String) = <summary>{str}</summary>

  def titleElem(str: String) =
    <idno type="msID">{str}</idno>

  def catalogueElem(str: String) =
    <idno type="catalogue">{str}</idno>

  def itemTitle(str: String) = <title>{str}</title>
  def originalItemTitle(str: String) = <title type="original">{str}</title>

  def mainLanguage(id: String, label: String) =
    <textLang mainLang={id} source="IANA">{label}</textLang>
  def otherLanguage(id: String, label: String) =
    <textLang otherLangs={id} source="IANA">{label}</textLang>

}
