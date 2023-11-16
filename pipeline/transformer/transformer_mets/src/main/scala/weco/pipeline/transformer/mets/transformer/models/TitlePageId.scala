package weco.pipeline.transformer.mets.transformer.models

import scala.xml.Elem

/** Returns the physical ID for the TitlePage element if it exists. */
object TitlePageId extends XMLOps {
  def apply(root: Elem): Option[String] = {
    implicit val r: Elem = root
    logicalTitlePageId.flatMap(smLink)
  }

  /** Find the id of the div in the logical structMap that corresponds to the
    * TitlePage, if present.
    *
    * A METS document should contain a LOGICAL structMap section, with
    * descendent divs containing an ID and a TYPE attribute:
    *
    * {{{
    * <mets:structMap TYPE="LOGICAL">
    *    <mets:div ADMID="AMD" DMDID="DMDLOG_0000" ID="LOG_0000" LABEL="[Report 1942] /" TYPE="Monograph">
    *      <mets:div ID="LOG_0001" TYPE="Cover" />
    *      <mets:div ID="LOG_0002" TYPE="TitlePage" />
    *   </mets:div>
    * </mets:structMap>
    * }}}
    */
  private def logicalTitlePageId(implicit root: Elem): Option[String] = {
    (
      (root \ "structMap").filterByAttribute("TYPE", "LOGICAL") \\ "div"
    ).filterByAttribute("TYPE", "TitlePage").headOption.map(_ \@ "ID")
  }

  /** The structLink sections maps the logical and physical IDs represented in
    * the document:
    * {{{
    * <mets:structLink>
    *   <mets:smLink xlink:from="LOG_0000" xlink:to="PHYS_0001" />
    *   <mets:smLink xlink:from="LOG_0000" xlink:to="PHYS_0002" />
    *   <mets:smLink xlink:from="LOG_0001" xlink:to="PHYS_0001" />
    *   <mets:smLink xlink:from="LOG_0002" xlink:to="PHYS_0003" />
    * </mets:structLink>
    * }}}
    * For this input we would expect smLink("LOG_0001") to return "PHYS_001"
    */
  private def smLink(from: String)(implicit root: Elem): Option[String] = {
    (root \ "structLink" \ "smLink")
      .filterByAttribute("{http://www.w3.org/1999/xlink}from", from)
      .headOption
      .map(_ \@ "{http://www.w3.org/1999/xlink}to")
  }
}
