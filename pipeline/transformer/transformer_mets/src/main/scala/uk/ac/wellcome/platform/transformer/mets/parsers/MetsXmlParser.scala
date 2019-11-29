package uk.ac.wellcome.platform.transformer.mets.parsers

import uk.ac.wellcome.platform.transformer.mets.transformer.Mets

import scala.util.Try
import scala.xml.{Elem, XML}

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
      thumbnailUrl <- thumbnailUrl(root)
    } yield
      Mets(
        recordIdentifier = id,
        accessCondition = maybeAccessCondition,
        thumbnailUrl = thumbnailUrl,
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

  private def thumbnailUrl(root: Elem): Either[Exception, Option[String]] =
    Right(None)
}
