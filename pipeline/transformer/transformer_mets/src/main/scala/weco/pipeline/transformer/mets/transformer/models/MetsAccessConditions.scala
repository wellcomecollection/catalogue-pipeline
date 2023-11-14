package weco.pipeline.transformer.mets.transformer.models

import grizzled.slf4j.Logging

import scala.xml.Elem

case class MetsAccessConditions(
  dz: Option[String],
  status: Option[String],
  usage: Option[String]
)

object MetsAccessConditions extends XMLOps with Logging {
  def apply(root: Elem): MetsAccessConditions = {
    implicit val r: Elem = root
    new MetsAccessConditions(
      dz = accessConditionDz,
      status = accessConditionStatus,
      usage = accessConditionUsage
    )
  }

  /** For licenses we are interested with the access condition with type `dz`.
    * For example:
    *
    * {{{
    * <mets:dmdSec ID="DMDLOG_0000">
    *   <mets:mdWrap MDTYPE="MODS">
    *     <mets:xmlData>
    *       <mods:mods>
    *         ...
    *         <mods:accessCondition type="dz">CC-BY-NC</mods:accessCondition>
    *         <mods:accessCondition type="player">63</mods:accessCondition>
    *         <mods:accessCondition type="status">Open</mods:accessCondition>
    *         ...
    *       </mods:mods>
    *     </mets:xmlData>
    *   </mets:mdWrap>
    * </mets:dmdSec>
    * }}}
    * The expected output would be: "CC-BY-NC"
    */
  def accessConditionDz(
    implicit root: Elem
  ): Option[String] =
    accessConditionWithType("dz")

  /** Here we extract the accessCondition of type `status`: For example:
    *
    * {{{
    * <mets:dmdSec ID="DMDLOG_0000">
    *    <mets:mdWrap MDTYPE="MODS">
    *       <mets:xmlData>
    *         <mods:mods>
    *            ...
    *            <mods:accessCondition type="dz">CC-BY-NC</mods:accessCondition>
    *            <mods:accessCondition type="player">63</mods:accessCondition>
    *            <mods:accessCondition type="status">Open</mods:accessCondition>
    *            ...
    *          </mods:mods>
    *        </mets:xmlData>
    *     </mets:mdWrap>
    * </mets:dmdSec>
    * }}}
    * The expected output would be: "Open"
    */
  def accessConditionStatus(
    implicit root: Elem
  ): Option[String] =
    accessConditionWithType("status")

  /** Here we extract the accessCondition of type `usage`: For example:
    *
    * {{{
    * <mets:dmdSec ID="DMDLOG_0000">
    *    <mets:mdWrap MDTYPE="MODS">
    *       <mets:xmlData>
    *         <mods:mods>
    *            ...
    *            <mods:accessCondition type="dz">CC-BY-NC</mods:accessCondition>
    *            <mods:accessCondition type="player">63</mods:accessCondition>
    *            <mods:accessCondition type="status">Open</mods:accessCondition>
    *            <mods:accessCondition type="usage">Some terms</mods:accessCondition>
    *            ...
    *          </mods:mods>
    *        </mets:xmlData>
    *     </mets:mdWrap>
    * </mets:dmdSec>
    * }}}
    *
    * The expected output would be: "Some terms"
    */
  def accessConditionUsage(
    implicit root: Elem
  ): Option[String] =
    accessConditionWithType("usage")

  /** Retrieve the accessCondition node in the document with given type. */
  private def accessConditionWithType(
    typeAttrib: String
  )(implicit root: Elem): Option[String] = {
    val nodes = (root \\ "dmdSec" \ "mdWrap" \\ "accessCondition")
      .filterByAttribute("type", typeAttrib)
      .toList
    nodes match {
      case Nil        => None
      case List(node) => Some(node.text)
      case _ =>
        warn(
          s"Found multiple accessConditions with type $typeAttrib in METS XML"
        )
        Some(nodes.head.text)
    }
  }

}
