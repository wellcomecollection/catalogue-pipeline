package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData
import uk.ac.wellcome.platform.transformer.sierra.transformers.subjects.{
  SierraBrandNameSubjects,
  SierraConceptSubjects,
  SierraMeetingSubjects,
  SierraOrganisationSubjects,
  SierraPersonSubjects
}
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Subject
import weco.catalogue.source_model.sierra.SierraBibNumber

object SierraSubjects extends SierraIdentifiedDataTransformer {

  type Output = List[Subject[IdState.Unminted]]

  val subjectsTransformers = List(
    SierraConceptSubjects,
    SierraPersonSubjects,
    SierraOrganisationSubjects,
    SierraMeetingSubjects,
    SierraBrandNameSubjects
  )

  def apply(bibId: SierraBibNumber, bibData: SierraBibData): List[Subject[IdState.Unminted]] =
    subjectsTransformers
      .flatMap(transform => transform(bibId, bibData))
      .distinct
}
