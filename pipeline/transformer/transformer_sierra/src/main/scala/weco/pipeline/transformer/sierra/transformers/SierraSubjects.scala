package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Subject
import weco.catalogue.source_model.sierra.SierraBibData
import weco.catalogue.source_model.sierra.identifiers.SierraBibNumber
import weco.pipeline.transformer.sierra.transformers.subjects.{SierraBrandNameSubjects, SierraConceptSubjects, SierraMeetingSubjects, SierraOrganisationSubjects, SierraPersonSubjects}

object SierraSubjects extends SierraIdentifiedDataTransformer {

  type Output = List[Subject[IdState.Unminted]]

  val subjectsTransformers = List(
    SierraConceptSubjects,
    SierraPersonSubjects,
    SierraOrganisationSubjects,
    SierraMeetingSubjects,
    SierraBrandNameSubjects
  )

  def apply(bibId: SierraBibNumber,
            bibData: SierraBibData): List[Subject[IdState.Unminted]] =
    subjectsTransformers
      .flatMap(transform => transform(bibId, bibData))
      .distinct
}
