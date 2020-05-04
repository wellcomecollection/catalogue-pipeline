package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.work.internal.{Subject, Unminted}
import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData
import uk.ac.wellcome.platform.transformer.sierra.transformers.subjects.{SierraBrandNameSubjects, SierraConceptSubjects, SierraMeetingSubjects, SierraOrganisationSubjects, SierraPersonSubjects}
import uk.ac.wellcome.sierra_adapter.model.SierraBibNumber

object SierraSubjects extends SierraTransformer {

  type Output = List[Subject[Unminted]]

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
