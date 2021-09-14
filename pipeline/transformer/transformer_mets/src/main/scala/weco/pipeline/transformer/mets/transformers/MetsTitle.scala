package weco.pipeline.transformer.mets.transformers

import weco.pipeline.transformer.result.Result

import scala.xml.Elem

object MetsTitle {

  /** The title is encoded in the METS.  For example:
    *
    * <mets:dmdSec ID="DMDLOG_0000">
    *   <mets:mdWrap MDTYPE="MODS">
    *     <mets:xmlData>
    *       <mods:mods>
    *         <mods:titleInfo>
    *           <mods:title>Reduction and treatment of a fracture of the calcaneus</mods:title>
    *         </mods:titleInfo>
    *       </mods:mods>
    *     </mets:xmlData>
    *   </mets:mdWrap>
    * </mets:dmdSec>
    *
    * The title is "Reduction and treatment of a fracture of the calcaneus"
    */
  def apply(root: Elem): Result[String] = {
    val titleNodes =
      (root \\ "dmdSec" \ "mdWrap" \\ "titleInfo" \ "title").toList.distinct

    titleNodes match {
      case Nil   => Left(new Throwable("Could not parse title from METS XML"))
      case nodes => Right(nodes.map(_.text).mkString(" "))
    }
  }
}
