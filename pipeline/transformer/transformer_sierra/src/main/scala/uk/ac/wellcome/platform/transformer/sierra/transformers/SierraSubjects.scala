package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber
import uk.ac.wellcome.models.work.internal.{
  AbstractRootConcept,
  Unminted,
  Subject
}
import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData
import uk.ac.wellcome.platform.transformer.sierra.transformers.subjects.{
  SierraBrandNameSubjects,
  SierraConceptSubjects,
  SierraMeetingSubjects,
  SierraOrganisationSubjects,
  SierraPersonSubjects
}

object SierraSubjects extends SierraTransformer {

  type Output = List[
    Unminted[
      Subject[
        Unminted[AbstractRootConcept]
      ]
    ]
  ]

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
