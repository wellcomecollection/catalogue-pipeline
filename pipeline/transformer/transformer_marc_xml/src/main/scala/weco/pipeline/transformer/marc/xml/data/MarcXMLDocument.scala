package weco.pipeline.transformer.marc.xml.data

import scala.xml.Node

/*
 * Represents a MARC XML Document as sent by EBSCO
 *
 * A Document consists of a <collection> element containing multiple <record> elements
 * */
case class MarcXMLDocument(collectionElement: Node) {
  def records: Seq[MarcXMLRecord] =
    (collectionElement \ "records").view.map(MarcXMLRecord)
}
