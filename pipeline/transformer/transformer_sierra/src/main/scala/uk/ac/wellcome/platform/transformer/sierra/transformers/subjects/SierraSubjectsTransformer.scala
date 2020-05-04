package uk.ac.wellcome.platform.transformer.sierra.transformers.subjects

import uk.ac.wellcome.platform.transformer.sierra.transformers.SierraTransformer
import uk.ac.wellcome.platform.transformer.sierra.source.{SierraBibData, SierraQueryOps, VarField}
import uk.ac.wellcome.models.work.internal.{Subject, Unminted}

trait SierraSubjectsTransformer extends SierraTransformer with SierraQueryOps {

  type Output = List[Subject[Unminted]]

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
}
