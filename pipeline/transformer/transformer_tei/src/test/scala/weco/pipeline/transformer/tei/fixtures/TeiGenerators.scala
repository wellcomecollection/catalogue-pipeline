package weco.pipeline.transformer.tei.fixtures

import org.scalatest.Suite
import weco.fixtures.RandomGenerators

import scala.xml.{Elem, NodeSeq, XML}

trait TeiGenerators extends RandomGenerators { this: Suite =>
  def sierraIdentifiers(bnumber: String) =
    <altIdentifier type="Sierra">
        <idno>{bnumber} </idno>
      </altIdentifier>

  def summary(str: String) = <summary>{str}</summary>

  def titleElem(str: String) = <titleStmt><title>{str}</title></titleStmt>

  def itemTitle(str: String) = <title>{str}</title>
  def originalItemTitle(str: String) = <title type="original">{str}</title>

  def msItem(id: String, titles: List[Elem] = Nil) =
    <msItem xml:id={id}>
      {titles}
    </msItem>

  def teiXml(
    id: String = randomAlphanumeric(),
    title: Elem = titleElem("test title"),
    identifiers: Option[Elem] = None,
    summary: Option[Elem] = None,
    languages: List[String] = Nil,
    items: List[Elem] = Nil
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
              <msContents>
                {summary.getOrElse(NodeSeq.Empty)}
                {languages.map(XML.loadString)}
                {items}
              </msContents>
            </msDesc>
          </sourceDesc>
        </fileDesc>
      </teiHeader>
    </TEI>
}
