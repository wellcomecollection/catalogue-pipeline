package weco.pipeline.transformer.tei

import weco.pipeline.transformer.result.Result

import scala.util.Try
import scala.xml.{Node, NodeSeq}

object TeiOps {

  /** The summary of the TEI is in the `summary` node under `msContents`. There
    * is supposed to be only one summary node <TEI
    * xmlns="http://www.tei-c.org/ns/1.0" xml:id="manuscript_15651"> <teiHeader>
    * <fileDesc> <sourceDesc> <msDesc xml:lang="en" xml:id="MS_Arabic_1">
    * <msContents> <summary>1 copy of al-Qānūn fī al-ṭibb by Avicenna,
    * 980-1037</summary> ... </TEI>
    */
  def summary(nodeSeq: NodeSeq): Result[Option[String]] = {
    (nodeSeq \ "msContents" \ "summary").toList match {
      case List(node) =>
        // some summary nodes can contain TEI specific xml tags, so we remove them
        Right(
          Some(
            node.text.trim
              .replaceAll("""<p(\s+\S+=".+?")+\s*(/)?>""", "<p$2>")
              .replaceAll("""(?!</?p\s*/?>)<.*?>""", "")
          )
        )
      case Nil => Right(None)
      case _   => Left(new RuntimeException("More than one summary node!"))
    }
  }

  def getIdFrom(node: Node): Result[String] =
    Try(
      node.attributes
        .collectFirst {
          case metadata if metadata.key == "id" => metadata.value.text.trim
        }
        .getOrElse(throw new RuntimeException("Could not find an id in node!"))
    ).toEither
}
