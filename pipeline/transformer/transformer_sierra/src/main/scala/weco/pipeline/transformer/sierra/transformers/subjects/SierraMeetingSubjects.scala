package weco.pipeline.transformer.sierra.transformers.subjects

import weco.pipeline.transformer.marc_common.models.MarcField
import weco.pipeline.transformer.marc_common.transformers.subjects.MarcMeetingSubject

object SierraMeetingSubjects extends SierraSubjectsTransformer {
  private object SierraMeetingSubject extends MarcMeetingSubject {
    override protected val defaultSecondIndicator: String = "0"
  }
  override protected def getSubject(
    field: MarcField
  ): OptionalSingleOutput =
    SierraMeetingSubject(field)

  override protected val subjectVarFields: Seq[String] = List("611")

}
