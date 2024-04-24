package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Subject
import weco.pipeline.transformer.sierra.transformers.subjects.{
  SierraBrandNameSubjects,
  SierraConceptSubjects,
  SierraMeetingSubjects,
  SierraOrganisationSubjects,
  SierraPersonSubjects,
  SierraSubjectsTransformer2
}
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.identifiers.SierraBibNumber

object SierraSubjects extends SierraIdentifiedDataTransformer {
  type Output = List[Subject[IdState.Unminted]]
  import weco.pipeline.transformer.marc_common.OntologyTypeOps._

  val subjectsTransformers: List[SierraSubjectsTransformer2] = List(
    SierraConceptSubjects,
    SierraPersonSubjects,
    SierraOrganisationSubjects,
    SierraMeetingSubjects,
    SierraBrandNameSubjects
  )

  def apply(
    bibId: SierraBibNumber,
    bibData: SierraBibData
  ): List[Subject[IdState.Unminted]] =
    subjectsTransformers
      .flatMap(transform => transform(bibId, bibData))
      .harmoniseOntologyTypes
}
