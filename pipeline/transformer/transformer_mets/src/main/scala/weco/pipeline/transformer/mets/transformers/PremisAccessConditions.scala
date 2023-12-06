package weco.pipeline.transformer.mets.transformers
import weco.pipeline.transformer.result.Result

import scala.xml.Elem

case class PremisAccessConditions(
  copyrightNote: Option[String],
  useRightsGrantedNote: Option[String]
) extends AccessConditionsParser {
  def parse: Result[MetsAccessConditions] =
    for {
      accessStatus <- MetsAccessStatus(useRightsGrantedNote)
      licence <- MetsLicence(copyrightNote)
    } yield MetsAccessConditions(
      accessStatus = accessStatus,
      licence = licence,
      usage = None
    )
}

object PremisAccessConditions {
  def apply(rightsMd: Elem): PremisAccessConditions = {
    val rightsStatement = rightsMd \ "mdWrap" \ "xmlData" \ "rightsStatement"
    val copyrightNoteElem =
      (rightsStatement \ "copyrightInformation" \ "copyrightNote").headOption
    val useRightsElem =
      ((rightsStatement \ "rightsGranted").filter(
        n => (n \ "act").text == "use"
      ) \ "rightsGrantedNote").headOption
    PremisAccessConditions(
      copyrightNote = copyrightNoteElem.map(_.text),
      useRightsGrantedNote = useRightsElem.map(_.text)
    )
  }
}
