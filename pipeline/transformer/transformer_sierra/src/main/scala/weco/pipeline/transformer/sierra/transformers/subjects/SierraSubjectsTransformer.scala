package weco.pipeline.transformer.sierra.transformers.subjects

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Subject
import weco.pipeline.transformer.sierra.transformers.{SierraAbstractConcepts, SierraIdentifiedDataTransformer}
import weco.pipeline.transformer.text.TextNormalisation._
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.identifiers.SierraBibNumber
import weco.sierra.models.marc.VarField

trait SierraSubjectsTransformer
    extends SierraIdentifiedDataTransformer
    with SierraAbstractConcepts
    with SierraQueryOps {

  type Output = List[Subject[IdState.Unminted]]

  val subjectVarFields: List[String]

  def apply(bibId: SierraBibNumber, bibData: SierraBibData): Output =
    getSubjectsFromVarFields(
      bibId,
      subjectVarFields.flatMap(bibData.varfieldsWithTag(_))
    )

  def getSubjectsFromVarFields(bibId: SierraBibNumber,
                               varFields: List[VarField]): Output

  /** Given a varField and a list of subfield tags, create a label by
    * concatenating the contents of every subfield with one of the given tags.
    *
    * The order is the same as that in the original MARC.
    *
    */
  def createLabel(varField: VarField, subfieldTags: List[String]): String =
    varField
      .subfieldsWithTags(subfieldTags: _*)
      .contents
      .mkString(" ")
      .trimTrailingPeriod

  /** Create an identifier for a subject created from an Agent.
   *
   * Note that these rules are different from the rules for Agents created
   * for contributors, because subjects and contributors are drawn from
   * different MARC fields with different behaviours.
   *
   */
  def identifyAgentSubject(varfield: VarField, ontologyType: String): IdState.Unminted = {
    varfield.indicator2 match {
      // 0 indicates LCNames id is in use.
      // This is a Wellcome-specific convention. MARC 0 for these fields (e.g. https://www.loc.gov/marc/bibliographic/bd610.html)
      // states that 0 means LCSH.
      // In this example from b17950235 (fd6nk8fw), n50082847 is the LCNames id for Glaxo Laboratories
      // 110 2  Glaxo Laboratories.|0n  50082847
      case Some("0") | Some("") | None =>
        getIdState(ontologyType, varfield)
      // Other values of second indicator show that the id is in an unusable scheme.
      // Do not identify.
      case _ =>
        info(
          s"${varfield.indicator2} is an unusable 2nd indicator value for an Agent in $varfield")
        IdState.Unidentifiable
    }
  }
}
