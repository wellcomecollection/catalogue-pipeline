package uk.ac.wellcome.platform.transformer.mets.parsers

import scala.collection.immutable.ListMap
import scala.util.Try
import scala.xml.{Elem, XML}

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
      .filter(_ \@ "type" == "dz")
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
    physicalStructMap(root)
      .headOption
      .flatMap { case (_, fileId) =>
        fileObjects(root).get(fileId)
      }
      .map(_.stripPrefix("objects/"))

  private def fileObjects(root: Elem): Map[String, String] =
    (root \ "fileSec" \ "fileGrp")
      .toList
      .filter(_ \@ "USE" == "OBJECTS")
      .flatMap(_ \ "file")
      .map { node =>
        val id = node \@ "ID"
        val location = (node  \ "FLocat")
          .toList
          .headOption
          .map(_ \@ "{http://www.w3.org/1999/xlink}href")
        (id, location)
      }
      .collect {
        case (id, Some(location)) if location.nonEmpty => (id, location)
      }
      .toMap

  private def physicalStructMap(root: Elem): ListMap[String, String] = {
    val physicalMappings =
      (root \ "structMap")
        .toList
        .filter(_ \@ "TYPE" == "PHYSICAL")
        .flatMap(_ \\ "div")
        .map { node =>
          val id = node \@ "ID"
          val fileId = (node \ "fptr").toList.headOption.map(_ \@ "FILEID")
          (id, fileId)
        }
        .collect {
          case (id, Some(fileId)) if fileId.nonEmpty => (id, fileId)
        }
    ListMap(physicalMappings:_*)
  }
}
