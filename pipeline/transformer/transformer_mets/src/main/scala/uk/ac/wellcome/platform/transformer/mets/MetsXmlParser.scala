package uk.ac.wellcome.platform.transformer.mets

import scala.xml.{Elem, XML}

import uk.ac.wellcome.models.work.internal.UnidentifiedWork

case class MetsData(
  recordIdentifier: Option[String],
  accessCondition: Option[String]
) {

  def toWork: UnidentifiedWork =
    throw new NotImplementedError
}

object MetsXmlParser {

  def apply(str: String): MetsData =
    MetsXmlParser(XML.loadString(str))

  def apply(root: Elem): MetsData =
    MetsData(
      recordIdentifier = recordIdentifier(root),
      accessCondition = accessCondition(root),
    )

  private def recordIdentifier(root: Elem): Option[String] =
    (root \\ "recordIdentifier").headOption
      .map(_.text)

  private def accessCondition(root: Elem): Option[String] =
    (root \\ "accessCondition")
      .filter(node => node \@ "type" == "dz")
      .headOption
      .map(_.text)
}
