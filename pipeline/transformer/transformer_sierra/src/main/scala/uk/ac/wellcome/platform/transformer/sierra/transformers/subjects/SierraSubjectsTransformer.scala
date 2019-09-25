package uk.ac.wellcome.platform.transformer.sierra.transformers.subjects

import uk.ac.wellcome.platform.transformer.sierra.transformers.{SierraTransformer, MarcUtils}
import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber
import uk.ac.wellcome.platform.transformer.sierra.source.{SierraBibData, VarField}
import uk.ac.wellcome.models.work.internal.{
  AbstractRootConcept,
  MaybeDisplayable,
  Subject
}

trait SierraSubjectsTransformer extends SierraTransformer with MarcUtils {

  type Output = List[
    MaybeDisplayable[
      Subject[
        MaybeDisplayable[AbstractRootConcept]
      ]
    ]
  ]

  val subjectVarFields: List[String]

  def apply(bibId: SierraBibNumber, bibData: SierraBibData) =
    getSubjectsFromVarFields(
      bibId,
      subjectVarFields.flatMap(getMatchingVarFields(bibData, _))
    )

  def getSubjectsFromVarFields(bibId: SierraBibNumber, varFields: List[VarField]): Output
}
