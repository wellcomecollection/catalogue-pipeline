package weco.pipeline.transformer.mets.transformers
import grizzled.slf4j.Logging
import weco.pipeline.transformer.result.Result

import scala.xml.Node

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

object PremisAccessConditions extends Logging{

  /** The access conditions are encoded a premis elementin the METS. For
    * example:
    * {{{
    *     <mets:rightsMD ID="rightsMD_1">
    *         <mets:mdWrap MDTYPE="PREMIS:RIGHTS">
    *             <mets:xmlData>
    *                 <premis:rightsStatement xmlns:premis="http://www.loc.gov/premis/v3" xsi:schemaLocation="http://www.loc.gov/premis/v3 http://www.loc.gov/standards/premis/v3/premis.xsd">
    *                     <premis:rightsBasis>Copyright</premis:rightsBasis>
    *                     <premis:copyrightInformation>
    *                         <premis:copyrightStatus>copyrighted</premis:copyrightStatus>
    *                         <premis:copyrightJurisdiction>UK</premis:copyrightJurisdiction>
    *                         <premis:copyrightStatusDeterminationDate />
    *                         <premis:copyrightNote>In copyright</premis:copyrightNote>
    *                     </premis:copyrightInformation>
    *                     <premis:rightsGranted>
    *                         <premis:act>use</premis:act>
    *                         <premis:rightsGrantedNote>Open</premis:rightsGrantedNote>
    *                     </premis:rightsGranted>
    *                 </premis:rightsStatement>
    *             </mets:xmlData>
    *         </mets:mdWrap>
    *     </mets:rightsMD>
    * }}}
    *
    * In this example,
    *   - The accessStatus is "Open"
    *   - The licence is "In Copyright"
    *     - There is no usage string
    */
  def apply(rightsMd: Node): PremisAccessConditions = {
    val rightsStatement = rightsMd \ "mdWrap" \ "xmlData" \ "rightsStatement"

    val rightsBasis = (rightsStatement \ "rightsBasis").headOption

    // Copyright specific information is held in the copyrightNote element
    val copyrightNoteElem =
      (rightsStatement \ "copyrightInformation" \ "copyrightNote").headOption

    // CC license information is indicated in the licenseNote element
    val licenseNoteElem =
      (rightsStatement \ "licenseInformation" \ "licenseNote").headOption

    // Following discussion with a.ray here:
    // https://wellcome.slack.com/archives/C02ANCYL90E/p1725026316294549
    val rightsNote = rightsBasis match {
      case Some(basis) if basis.text == "Copyright" => copyrightNoteElem
      case Some(basis) if basis.text == "License" => licenseNoteElem

      // If we don't have a rightsBasis, pick from either copyright or
      // license preferring copyright
      case _ =>
        warn(s"rightsBasis not found in rightsMD!")

        List(
          copyrightNoteElem,
          licenseNoteElem,
        ).flatten.headOption
    }

    val useRightsElem =
      ((rightsStatement \ "rightsGranted").filter(
        n => (n \ "act").text == "use"
      ) \ "rightsGrantedNote").headOption

    PremisAccessConditions(
      copyrightNote = rightsNote.map(_.text),
      useRightsGrantedNote = useRightsElem.map(_.text)
    )
  }
}
