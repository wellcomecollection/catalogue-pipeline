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

  /** Given a varField and a list of subfield tags, create a label by
    * concatenating the contents of every subfield with one of the given tags.
    *
    * The order is the same as that in the original MARC.
    *
    */
  def createLabel(varField: VarField, subfieldTags: List[String]): String =
    varField.subfields
      .filter { vf =>
        subfieldTags.contains(vf.tag)
      }
      .map { _.content }
      .mkString(" ")
}
