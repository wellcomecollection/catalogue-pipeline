package weco.pipeline.transformer.sierra.transformers.subjects

import weco.pipeline.transformer.marc_common.models.MarcField
import weco.pipeline.transformer.marc_common.transformers.subjects.MarcOrganisationSubject

object SierraOrganisationSubjects extends SierraSubjectsTransformer2 {
  override protected val subjectVarFields: List[String] = List("610")
  override protected def getSubject(
    field: MarcField
  ): OptionalSingleOutput =
    SierraOrganisationSubject(field)

  private object SierraOrganisationSubject extends MarcOrganisationSubject {
    override protected val defaultSecondIndicator: String = "0"
  }
}
