package weco.pipeline.transformer.ebsco.data

import scala.xml.{Elem, Node, NodeSeq}

/*
 * Represents a MARC XML Record as sent by EBSCO
 * A <record> consists of a <leader> element, some <controlfield> elements and some <datafield> elements.
 * */
case class EbscoMarcRecord(recordElement: Elem) {
  private def hasTag(values: String*)(node: Node) = {
    values.contains(node \@ "tag")
  }
//  private def hasTagAndSubfieldTag(values: (String, String)*)(node: Node) = {
//    values.exists {
//      value =>
//        node \@ "tag" == value._1
//        node \ "subfield" \@ "tag" == value._2
//    }
//  }

  def controlField(tag: String): Option[String] =
    recordElement \ "controlfield" filter hasTag(tag) match {
      case NodeSeq.Empty => None
      case Seq(node)     => Some(node.text)
      case _ =>
        None // TODO: warn that there are multiple matches and return (head.text)?
    }

  def datafieldsWithTags(tags: String*): Seq[DataField] =
    recordElement \ "datafield" filter hasTag(tags: _*) map (DataField(_))

  def datafieldsWithTag(tag: String): Seq[DataField] =
    datafieldsWithTags(tag)

  def subfieldsWithTags(tags: (String, String)*): List[Subfield] =
    tags.toList.flatMap {
      case (tag, subfieldTag) =>
        (recordElement \ "datafield" filter hasTag(
          tag
        )) \ "subfield" filter hasTag(subfieldTag) map (Subfield(_))
    }

  def subfieldsWithTag(tag: (String, String)): List[Subfield] =
    subfieldsWithTags(tag)

}
