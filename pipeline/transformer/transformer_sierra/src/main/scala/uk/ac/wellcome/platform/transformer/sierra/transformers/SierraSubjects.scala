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
  SierraPersonSubjects
}

object SierraSubjects
  extends SierraTransformer
    with SierraConceptSubjects
    with SierraPersonSubjects
    with SierraOrganisationSubjects {

  type Output = List[
    MaybeDisplayable[
      Subject[
        MaybeDisplayable[AbstractRootConcept]
      ]
    ]
  ]

  def apply(bibId: SierraBibNumber, bibData: SierraBibData) =
    getSubjectswithAbstractConcepts(bibData) ++
      getSubjectsWithPerson(bibData) ++
      getSubjectsWithOrganisation(bibId, bibData)
}
