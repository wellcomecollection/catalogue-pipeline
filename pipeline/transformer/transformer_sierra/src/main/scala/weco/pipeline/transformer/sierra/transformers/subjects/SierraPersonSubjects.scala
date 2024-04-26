package weco.pipeline.transformer.sierra.transformers.subjects

import weco.pipeline.transformer.marc_common.models.MarcField
import weco.pipeline.transformer.marc_common.transformers.subjects.MarcPersonSubject

// Populate wwork:subject
//
// Use MARC field "600" where the second indicator is not 7.
//
// The concepts come from:
//
//    - The person
//    - The contents of subfields $t and $x (title and general subdivision),
//      both as Concepts
//
// The label is constructed concatenating subfields $a, $b, $c, $d, $e,
// where $d and $e represent the person's dates and roles respectively.
//
// The person can be identified if there is an identifier in subfield $0 and the second indicator is "0".
// If second indicator is anything other than 0, we don't expose the identifier for now.
//
object SierraPersonSubjects extends SierraSubjectsTransformer {

  override protected val subjectVarFields: List[String] = List("600")

  override protected def getSubject(
    field: MarcField
  ): SierraPersonSubjects.OptionalSingleOutput = SierraPersonSubject(field)

  private object SierraPersonSubject extends MarcPersonSubject {
    override protected val defaultSecondIndicator: String = "0"
  }
}
