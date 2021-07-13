package weco.pipeline.transformer.tei.fixtures

import org.scalatest.Suite

import scala.xml.{Elem, NodeSeq}

trait TeiGenerators { this: Suite =>
  def sierraIdentifiers(bnumber: String) =
    <altIdentifier type="Sierra">
        <idno>{bnumber} </idno>
      </altIdentifier>

  def summary(str: String) = <summary>{str}</summary>

  def title(str: String) =  <titleStmt><title>{str}</title></titleStmt>

  def mainLanguage(id: String,label: String) =  <textLang mainLang={id} source="IANA">{label}</textLang>
  def otherLanguage(id: String,label: String) =  <textLang otherLangs={id} source="IANA">{label}</textLang>

  def teiXml(id: String,
             identifiers: Option[Elem] = None,
             summary: Option[Elem] = None,
             title: Option[Elem] = None,
             languages: List[Elem] = Nil) =
    <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id={id}>
      <teiHeader>
        <fileDesc>
        {title.getOrElse(NodeSeq.Empty)}
          <sourceDesc>
            <msDesc xml:lang="en" xml:id="MS_Arabic_1">
              <msIdentifier>
              {identifiers.getOrElse(NodeSeq.Empty)}
              </msIdentifier>
              <msContents>
                {summary.getOrElse(NodeSeq.Empty)}
                {languages}
              </msContents>
            </msDesc>
          </sourceDesc>
        </fileDesc>
      </teiHeader>
    </TEI>
}
