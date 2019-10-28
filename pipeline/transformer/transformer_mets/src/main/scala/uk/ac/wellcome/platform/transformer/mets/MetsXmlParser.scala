package uk.ac.wellcome.platform.transformer.mets

import scala.xml.{Elem, XML}

object MetsXmlParser {

  def apply(str: String): Option[MetsData] =
    MetsXmlParser(XML.loadString(str))

  def apply(root: Elem): Option[MetsData] =
    recordIdentifier(root).map { id =>
      MetsData(
        recordIdentifier = id,
        accessCondition = accessCondition(root),
      )
    }

  private def recordIdentifier(root: Elem): Option[String] =
    (root \\ "recordIdentifier").headOption
      .map(_.text)

  private def accessCondition(root: Elem): Option[String] =
    (root \\ "accessCondition")
      .filter(_ \@ "type" == "dz")
      .headOption
      .map(_.text)
}
