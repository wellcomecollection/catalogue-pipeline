package uk.ac.wellcome.platform.transformer.tei.transformer

import scala.util.Try
import scala.xml.{Elem, XML}

class TeiXml(xml: Elem) {
  val id: String = xml.attributes
    .collectFirst {
      case metadata if metadata.key == "id" => metadata.value.text
    }
    .getOrElse(throw new RuntimeException(s"Could not find an id in XML!"))

  def bNumber: Either[Throwable, Option[String]] = {
    val identifiersNodes = xml \\ "msDesc" \ "msContents" \ "msIdentifier" \ "altIdentifier"
    val seq = (identifiersNodes.filter(
      n => (n \@ "type").toLowerCase == "sierra"
    ) \ "idno").toList
    seq match {
      case List(node) => Right(Some(node.text.trim))
      case Nil        => Right(None)
      case _          => ???
    }
  }

  def summary: Either[Throwable, Option[String]] = {
    val nodes = (xml \\ "msDesc" \ "msContents" \ "summary").toList
    nodes match {
      case List(node) => Right(Some(node.text))
      case Nil        => Right(None)
      case _          => ???
    }
  }
}

object TeiXml {
  def apply(id: String, xmlString: String): Either[Throwable, TeiXml] =
    for {
      xml <- Try(XML.loadString(xmlString)).toEither
      teiXml = new TeiXml(xml)
      _ <- Try(
        require(
          teiXml.id == id,
          s"Supplied id $id did not match id in XML ${teiXml.id}"
        )
      ).toEither
    } yield teiXml
}
