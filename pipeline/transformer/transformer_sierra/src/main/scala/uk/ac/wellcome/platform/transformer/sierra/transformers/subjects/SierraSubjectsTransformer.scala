package uk.ac.wellcome.platform.transformer.sierra.transformers.subjects

import uk.ac.wellcome.platform.transformer.sierra.transformers.SierraIdentifiedDataTransformer
import weco.catalogue.internal_model.text.TextNormalisation._
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Subject
import weco.catalogue.source_model.sierra.marc.VarField
import weco.catalogue.source_model.sierra.source.SierraQueryOps
import weco.catalogue.source_model.sierra.{SierraBibData, SierraBibNumber}

trait SierraSubjectsTransformer
    extends SierraIdentifiedDataTransformer
    with SierraQueryOps {

  type Output = List[Subject[IdState.Unminted]]

  val subjectVarFields: List[String]

  def apply(bibId: SierraBibNumber, bibData: SierraBibData) =
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
}
