package weco.pipeline.transformer.tei.generators

import org.scalatest.Suite
import weco.fixtures.RandomGenerators

import scala.xml._

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
    authors: List[Elem] = Nil,
    handNotes: List[Elem] = Nil,
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
              <physDesc>
                <handDesc>
                  {handNotes}
                </handDesc>
              </physDesc>
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

  def author(label: String, key: Option[String] = None) = key match {
    case Some(k) => <author key={k}>{label}</author>
    case None    => <author>{label}</author>
  }

  def author(persNames: List[Elem], key: Option[String]) =
    key match {
      case Some(k) => <author key={k}>{persNames}</author>
      case None    => <author>{persNames}</author>
    }

  def handNotes(label: String = "", persNames: List[Elem] = Nil, scribe: Option[String] = None, locus: Option[Elem] = None) = {
    val scribeAttribute = scribe.map(s=>Attribute("scribe", Text(s), Null)).getOrElse(Null)
    <handNote>
      {locus.getOrElse(NodeSeq.Empty)}{label}{persNames}
  </handNote> % scribeAttribute
  }

  def locus(label: String,target: Option[String] = None) = target match {
case Some(t) => <locus target={t}>{label}</locus>
    case None => <locus>{label}</locus>
  }

  def scribe(name: String) = persName(label = name, role = Some("scr"))

  def persName(label: String,
               key: Option[String] = None,
               `type`: Option[String] = None,
               role: Option[String] = None) = {
    val attributes = Map("key" ->key, "type" -> `type`, "role" -> role).foldLeft(Null: MetaData) {
      case (metadata, (name, Some(value))) =>
        Attribute(name, Text(value), metadata)
      case (metadata, (_ ,None)) => metadata
    }
    <persName>{label}</persName> % attributes
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
