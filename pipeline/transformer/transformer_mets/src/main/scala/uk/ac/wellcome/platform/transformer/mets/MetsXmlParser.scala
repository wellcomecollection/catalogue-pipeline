package uk.ac.wellcome.platform.transformer.mets

import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, XML}

object MetsXmlParser {

  def apply(str: String): Try[Mets] =
    MetsXmlParser(XML.loadString(str))

  def apply(root: Elem): Try[Mets] =
    {
      for {
        id <- recordIdentifier(root)
        maybeAccessCondition <- accessCondition(root)
      } yield Mets(
        recordIdentifier = id,
        accessCondition = maybeAccessCondition,
      )
    }

  private def recordIdentifier(root: Elem): Try[String] = {
    val identifierNodes = (root \\ "dmdSec" \ "mdWrap" \\ "recordInfo" \ "recordIdentifier").toList
    identifierNodes match {
      case List(identifierNode) => Success(identifierNode.text)
      case _ => Failure(new Exception("Could not parse recordIdentifier from METS XML"))
    }
  }

  private def accessCondition(root: Elem): Try[Option[String]] = {
    val licenseNodes = (root \\ "dmdSec" \ "mdWrap" \\ "accessCondition")
      .filter(_ \@ "type" == "dz").toList
    licenseNodes match {
      case Nil => Success(None)
      case List(licenseNode) => Success(Some(licenseNode.text))
      case _ => Failure(new Exception("Found multiple accessCondtions in METS XML"))
    }
  }
}
