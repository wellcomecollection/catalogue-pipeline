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
    physDesc: Option[Elem] = None,
    origPlace: Option[Elem] = None,
    originDates: List[Elem] = Nil,
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
              {physDesc.getOrElse(NodeSeq.Empty)}
              <history>
                <origin>
                  {origPlace.getOrElse(NodeSeq.Empty)}
                  {originDates}
                </origin>
              </history>
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

  def physDesc(objectDesc: Option[Elem]= None, handNotes: List[Elem] = Nil) =
    <physDesc>
      {objectDesc.getOrElse(NodeSeq.Empty)}
      <handDesc>
        {handNotes}
      </handDesc>
    </physDesc>

  def handNotes(label: String = "",
                persNames: List[Elem] = Nil,
                scribe: Option[String] = None,
                locus: List[Elem] = Nil) = {
    val scribeAttribute =
      scribe.map(s => Attribute("scribe", Text(s), Null)).getOrElse(Null)
    <handNote>
      {locus}{label}{persNames}
  </handNote> % scribeAttribute
  }

  def objectDesc(material: Option[String] = None, support: Option[Elem] = None, extent: Option[Elem] = None) = {
    val materialAttribute =
      material.map(s => Attribute("material", Text(s), Null)).getOrElse(Null)
    <objectDesc>

      {<supportDesc>
      {extent.getOrElse(NodeSeq.Empty)}
      {support.getOrElse(NodeSeq.Empty)}
      </supportDesc> % materialAttribute}
    </objectDesc>
  }

  def support(supportLabel: String) = <support>{supportLabel}</support>

  def extent(label: String, dimensions: Option[Elem]= None) =
    <extent>
      {label}
      {dimensions.getOrElse(NodeSeq.Empty)}
    </extent>

  def dimensions(unit: String, height: String, width: String)=
    <dimensions unit={unit} type="leaf">
      <height>{height}</height>
      <width>{width}</width>
    </dimensions>

  def locus(label: String, target: Option[String] = None) = target match {
    case Some(t) => <locus target={t}>{label}</locus>
    case None    => <locus>{label}</locus>
  }

  def scribe(name: String, `type`: Option[String] = None) =
    persName(label = name, role = Some("scr"), `type` = `type`)

  def persName(label: String,
               key: Option[String] = None,
               `type`: Option[String] = None,
               role: Option[String] = None) = {
    val attributes = Map("key" -> key, "type" -> `type`, "role" -> role)
      .foldLeft(Null: MetaData) {
        case (metadata, (name, Some(value))) =>
          Attribute(name, Text(value), metadata)
        case (metadata, (_, None)) => metadata
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

  def origPlace(country: Option[String] = None,
                settlement: Option[String] = None,
                region: Option[String] = None,
                orgName: Option[String] = None,
                label: Option[String] = None) =
    <origPlace>
      <country>{country.getOrElse("")}</country>,
      <region>{region.getOrElse("")}</region>,
      <settlement>{settlement.getOrElse("")}</settlement>,
      <orgName>{orgName.getOrElse("")}</orgName>
      {label.getOrElse("")}
    </origPlace>

  def originDate(calendar: String, label: String) =
    <origDate calendar={calendar}>{label}</origDate>

}
