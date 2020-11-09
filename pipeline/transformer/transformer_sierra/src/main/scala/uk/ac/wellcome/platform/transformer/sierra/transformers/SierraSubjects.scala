package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.work.internal.{IdState, Subject}
import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData
import uk.ac.wellcome.platform.transformer.sierra.transformers.subjects.{
  SierraBrandNameSubjects,
  SierraConceptSubjects,
  SierraMeetingSubjects,
  SierraOrganisationSubjects,
  SierraPersonSubjects
}
import uk.ac.wellcome.sierra_adapter.model.SierraBibNumber

object SierraSubjects extends SierraIdentifiedDataTransformer {

  type Output = List[Subject[IdState.Unminted]]

  val subjectsTransformers = List(
    SierraConceptSubjects,
    SierraPersonSubjects,
    SierraOrganisationSubjects,
    SierraMeetingSubjects,
    SierraBrandNameSubjects
  )

  def apply(bibId: SierraBibNumber, bibData: SierraBibData) =
    subjectsTransformers.flatMap(transform => transform(bibId, bibData))
}
