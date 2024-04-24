package weco.pipeline.transformer.sierra.transformers.subjects

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Subject
import weco.pipeline.transformer.marc_common.transformers.subjects.MarcConceptSubject
import weco.pipeline.transformer.marc_common.models.MarcField

object SierraConceptSubjects extends SierraSubjectsTransformer2 {
  override protected val subjectVarFields: Seq[String] =
    Seq("650", "648", "651")

  override protected def getSubject(
    field: MarcField
  ): Option[Subject[IdState.Unminted]] = SierraConceptSubject(field)

  private object SierraConceptSubject extends MarcConceptSubject {
    override protected val defaultSecondIndicator: String = "0"
  }
}
