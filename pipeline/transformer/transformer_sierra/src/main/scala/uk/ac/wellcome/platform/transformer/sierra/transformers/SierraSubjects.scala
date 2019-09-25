package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber
import uk.ac.wellcome.models.work.internal.{
  AbstractRootConcept,
  MaybeDisplayable,
  Subject
}
import uk.ac.wellcome.platform.transformer.sierra.source.SierraBibData
import uk.ac.wellcome.platform.transformer.sierra.transformers.subjects.{
  SierraConceptSubjects,
  SierraOrganisationSubjects,
  SierraPersonSubjects,
  SierraMeetingSubjects,
}

object SierraSubjects extends SierraTransformer {

  type Output = List[
    MaybeDisplayable[
      Subject[
        MaybeDisplayable[AbstractRootConcept]
      ]
    ]
  ]

  val subjectsTransformers = List(
    SierraConceptSubjects,
    SierraPersonSubjects,
    SierraOrganisationSubjects,
    SierraMeetingSubjects
  )

  def apply(bibId: SierraBibNumber, bibData: SierraBibData) =
    subjectsTransformers.flatMap(transform => transform(bibId, bibData))
}
