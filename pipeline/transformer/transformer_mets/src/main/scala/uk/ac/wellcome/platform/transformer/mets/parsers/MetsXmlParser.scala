package uk.ac.wellcome.platform.transformer.mets.parsers

import scala.collection.immutable.ListMap
import scala.util.Try
import scala.xml.{Elem, XML, NodeSeq}

import uk.ac.wellcome.platform.transformer.mets.transformer.Mets

object MetsXmlParser {

  def apply(str: String): Either[Throwable, Mets] =
    for {
      is <- Try(XML.loadString(str)).toEither
      mets <- MetsXmlParser(is)
    } yield (mets)

  def apply(root: Elem): Either[Exception, Mets] = {
    for {
      id <- recordIdentifier(root)
      maybeAccessCondition <- accessCondition(root)
    } yield
      Mets(
        recordIdentifier = id,
        accessCondition = maybeAccessCondition,
        thumbnailLocation = thumbnailLocation(root),
      )
  }

  private def recordIdentifier(root: Elem): Either[Exception, String] = {
    val identifierNodes =
      (root \\ "dmdSec" \ "mdWrap" \\ "recordInfo" \ "recordIdentifier").toList
    identifierNodes match {
      case List(identifierNode) => Right[Exception, String](identifierNode.text)
      case _ =>
        Left[Exception, String](
          new Exception("Could not parse recordIdentifier from METS XML"))
    }
  }

  private def accessCondition(root: Elem): Either[Exception, Option[String]] = {
    val licenseNodes = (root \\ "dmdSec" \ "mdWrap" \\ "accessCondition")
      .filterByAttribute("type", "dz")
      .toList
    licenseNodes match {
      case Nil => Right(None)
      case List(licenseNode) =>
        Right[Exception, Option[String]](Some(licenseNode.text))
      case _ =>
        Left[Exception, Option[String]](
          new Exception("Found multiple accessCondtions in METS XML"))
    }
  }

  private def thumbnailLocation(root: Elem): Option[String] =
    physicalStructMap(root).headOption
      .flatMap {
        case (_, fileId) =>
          fileObjects(root).get(fileId)
      }
      .map(_.stripPrefix("objects/"))

  private def fileObjects(root: Elem): Map[String, String] =
    (root \ "fileSec" \ "fileGrp")
      .filterByAttribute("USE", "OBJECTS")
      .childrenWithTag("file")
      .toMapping(
        keyAttrib = "ID",
        valueNode = "FLocat",
        valueAttrib = "{http://www.w3.org/1999/xlink}href"
      )

  private def physicalStructMap(root: Elem): ListMap[String, String] =
    (root \ "structMap")
      .filterByAttribute("TYPE", "PHYSICAL")
      .descendentsWithTag("div")
      .toMapping(
        keyAttrib = "ID",
        valueNode = "fptr",
        valueAttrib = "FILEID"
      )

  implicit class NodeSeqOps(nodes: NodeSeq) {

    def filterByAttribute(attrib: String, value: String) =
      nodes.filter(_ \@ attrib == value)

    def childrenWithTag(tag: String) =
      nodes.flatMap(_ \ tag)

    def descendentsWithTag(tag: String) =
      nodes.flatMap(_ \\ tag)

    def toMapping(keyAttrib: String, valueNode: String, valueAttrib: String) = {
      val mappings = nodes
        .map { node =>
          val key = node \@ keyAttrib
          val value = (node \ valueNode).toList.headOption.map(_ \@ valueAttrib)
          (key, value)
        }
        .collect {
          case (key, Some(value)) if key.nonEmpty && value.nonEmpty =>
            (key, value)
        }
      ListMap(mappings: _*)
    }
  }
}
