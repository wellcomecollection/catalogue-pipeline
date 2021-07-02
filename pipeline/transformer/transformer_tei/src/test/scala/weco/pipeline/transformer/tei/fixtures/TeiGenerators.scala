package weco.pipeline.transformer.tei.fixtures

import org.scalatest.Suite

import scala.xml.{Elem, NodeSeq}

trait TeiGenerators { this: Suite =>
  def sierraIdentifiers(bnumber: String) =
    <altIdentifier type="Sierra">
        <idno>{bnumber} </idno>
      </altIdentifier>

  def summary(str: String) = <summary>{str}</summary>

  def teiXml(id: String,
             identifiers: Option[Elem] = None,
             summary: Option[Elem] = None) =
    <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id={id}>
      <teiHeader>
        <fileDesc>
          <sourceDesc>
            <msDesc xml:lang="en" xml:id="MS_Arabic_1">
              <msIdentifier>
              {identifiers.getOrElse(NodeSeq.Empty)}
              </msIdentifier>
              <msContents>
                {summary.getOrElse(NodeSeq.Empty)}
              </msContents>
            </msDesc>
          </sourceDesc>
        </fileDesc>
      </teiHeader>
    </TEI>
}
