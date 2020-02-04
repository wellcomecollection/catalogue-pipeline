package uk.ac.wellcome.platform.transformer.calm.transformers

import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber
import uk.ac.wellcome.models.work.internal.{Subject, Unminted}
import uk.ac.wellcome.platform.transformer.calm.source.SierraBibData
import uk.ac.wellcome.platform.transformer.calm.transformers.subjects.{
  SierraBrandNameSubjects,
  SierraConceptSubjects,
  SierraMeetingSubjects,
  SierraOrganisationSubjects,
  SierraPersonSubjects
}

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
