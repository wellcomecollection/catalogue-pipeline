package weco.pipeline.transformer.mets.transformer.models

import scala.xml.NodeSeq

trait XMLOps {

  implicit class NodeSeqOps(nodes: NodeSeq) {

    def filterByAttribute(attrib: String, value: String): NodeSeq =
      nodes.filter(_ \@ attrib == value)
    def filterByHasChild(tag: String): NodeSeq =
      nodes.filter(_ \ tag != Nil)
    def childrenWithTag(tag: String): NodeSeq =
      nodes.flatMap(_ \ tag)

    def descendentsWithTag(tag: String): NodeSeq =
      nodes.flatMap(_ \\ tag)

    def sortByAttribute(attrib: String): NodeSeq =
      nodes.sortBy(_ \@ attrib)

    def toMapping(
      keyAttrib: String,
      valueAttrib: String,
      valueNode: Option[String] = None
    ): Seq[(String, String)] =
      nodes
        .map {
          node =>
            val key = node \@ keyAttrib
            val value = valueNode
              .flatMap(tag => (node \ tag).headOption)
              .orElse(Some(node))
              .map(_ \@ valueAttrib)
            (key, value)
        }
        .collect {
          case (key, Some(value)) if key.nonEmpty && value.nonEmpty =>
            (key, value)
        }
  }
}
