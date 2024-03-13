package weco.pipeline.transformer.marc.xml.data

import weco.pipeline.transformer.marc_common.models.{
  MarcField,
  MarcRecord,
  MarcSubfield
}

import scala.xml.{Node, NodeSeq}

/*
 * Represents a MARC XML Record as sent by EBSCO
 * A <record> consists of a <leader> element, some <controlfield> elements and some <datafield> elements.
 * This class knows nothing of the behaviour and meaning of any MARC fields.
 * It provides functionality to access fields by their tags.
 * */
case class MarcXMLRecord(recordElement: Node) extends MarcRecord {

  lazy val leader: String = recordElement \ "leader" text
  def controlField(tag: String): Option[String] =
    recordElement \ "controlfield" filter hasTag(tag) match {
      case NodeSeq.Empty => None
      case Seq(node)     => Some(node.text)
      case _ =>
        None // TODO: warn that there are multiple matches and return (head.text)?
    }

  def fieldsWithTags(tags: String*): Seq[MarcField] =
    recordElement \ "datafield" filter hasTag(tags: _*) map (MarcXMLDataField(
      _
    ))

  def fieldsWithTag(tag: String): Seq[MarcField] =
    fieldsWithTags(tag)

  def subfieldsWithTags(tags: (String, String)*): List[MarcSubfield] =
    tags.toList.flatMap {
      case (tag, subfieldTag) =>
        (recordElement \ "datafield" filter hasTag(
          tag
        )) \ "subfield" filter hasCode(subfieldTag) map (MarcXMLSubfield(_))
    }

  private def hasTag(values: String*)(node: Node) = {
    values.contains(node \@ "tag")
  }
  
  private def hasCode(values: String*)(node: Node) = {
    values.contains(node \@ "code")
  }

  override val fields: Seq[MarcField] =
    recordElement \ "datafield" map (node => MarcXMLDataField(node))

  override def subfieldsWithTag(tagPair: (String, String)): List[MarcSubfield] =
    subfieldsWithTags(tagPair)
}
