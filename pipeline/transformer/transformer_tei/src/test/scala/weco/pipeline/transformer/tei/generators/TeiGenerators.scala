package weco.pipeline.transformer.tei.generators

import org.scalatest.Suite
import weco.fixtures.RandomGenerators

import scala.xml._

trait TeiGenerators extends RandomGenerators { this: Suite =>
  def teiXml(
    id: String = randomAlphanumeric(),
    refNo: NodeSeq = idnoMsId("test title"),
    identifiers: Option[Elem] = None,
    summary: Option[Elem] = None,
    languages: List[Elem] = Nil,
    items: List[Elem] = Nil,
    parts: List[Elem] = Nil,
    catalogues: List[Elem] = Nil,
    authors: List[Elem] = Nil,
    physDesc: Option[Elem] = None,
    history: Option[Elem] = None,
    profileDesc: Option[Elem] = None
  ): Elem =
    <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id={id}>
      <teiHeader>
        <fileDesc>
          <publicationStmt>
            {refNo}
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
              {history.getOrElse(NodeSeq.Empty)}
            </msDesc>
          </sourceDesc>
        </fileDesc>
        {profileDesc.getOrElse(NodeSeq.Empty)}
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
    authors: List[Elem] = Nil,
    locus: Option[Elem] = None
  ) =
    <msItem xml:id={id}>
      {locus.getOrElse(NodeSeq.Empty)}
      {titles}
      {languages}
      {items}
      {authors}
    </msItem>

  def msPart(
    id: String,
    msContents: Option[Elem] = None,
    physDesc: Option[Elem] = None,
    history: Option[Elem] = None
  ) =
    <msPart xml:id={id}>
      {msContents.getOrElse(NodeSeq.Empty)}
      {physDesc.getOrElse(NodeSeq.Empty)}
      {history.getOrElse(NodeSeq.Empty)}
    </msPart>

  def history(origPlace: Option[Elem] = None, originDates: List[Elem] = Nil, provenance:List[Elem] = Nil): Elem =
    <history>
      <origin>
        {origPlace.getOrElse(NodeSeq.Empty)}
        {originDates}
      </origin>
      {provenance}
    </history>

  def provenance(
                  str: String,
                  when: Option[String] = None,
                  notBefore: Option[String] = None,
                  from: Option[String] = None,
                  to: Option[String] = None,
                  notAfter: Option[String] = None
                ): Elem = {
    val attributes = Map(
      "when" -> when,
      "notBefore" -> notBefore,
      "from" -> from, "to" -> to,
      "notAfter" -> notAfter,
    ).foldLeft(Null: MetaData) {
        case (metadata, (name, Some(value))) =>
          Attribute(name, Text(value), metadata)
        case (metadata, (_, None)) => metadata
      }
    <provenance>{str}</provenance> % attributes
  }

  def profileDesc(keywords: List[Elem]) = <profileDesc>
      <textClass>
        {keywords}
      </textClass>
    </profileDesc>

  def keywords(keywordsScheme: Option[String] = None,
               subjects: NodeSeq = Nil) = {
    val schemeAttribute = keywordsScheme
      .map(s => Attribute("scheme", Text(s), Null))
      .getOrElse(Null)
    <keywords>
        <list>
          {subjects}
        </list>
      </keywords> % schemeAttribute
  }

  def subject(label: String, reference: Option[String] = None) = {
    val referenceAttribute =
      reference.map(s => Attribute("ref", Text(s), Null)).getOrElse(Null)
    <item>
          {<term>{label}</term> % referenceAttribute}
        </item>
  }

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

  def physDesc(objectDesc: Option[Elem] = None, handNotes: List[Elem] = Nil) =
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

  def objectDesc(material: Option[String] = None,
                 support: Option[Elem] = None,
                 extent: Option[Elem] = None) = {
    val materialAttribute =
      material.map(s => Attribute("material", Text(s), Null)).getOrElse(Null)
    <objectDesc>

      {<supportDesc>
      {extent.getOrElse(NodeSeq.Empty)}
      {support.getOrElse(NodeSeq.Empty)}
      </supportDesc> % materialAttribute}
    </objectDesc>
  }

  def support(supportLabel: String,
              watermarks: List[Elem] = Nil,
              measures: List[Elem] = Nil) =
    <support>
      {supportLabel}
      {watermarks}
      {measures}
    </support>

  def watermark(label: String) = <watermark>{label}</watermark>

  def extent(label: String, dimensions: List[Elem] = Nil) =
    <extent>
      {label}
      {dimensions}
    </extent>

  def dimensions(unit: String, `type`: String, height: String, width: String) =
    <dimensions unit={unit} type={`type`}>
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

  def idnoMsId(str: String) =
    <idno type="msID">{str}</idno>

  def catalogueElem(str: String) =
    <idno type="catalogue">{str}</idno>

  def itemTitle(str: String) = <title>{str}</title>
  def originalItemTitle(str: String) = <title type="original">{str}</title>
  def standardItemTitle(str: String) = <title type="standard">{str}</title>

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
