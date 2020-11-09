package uk.ac.wellcome.platform.transformer.sierra.transformers.subjects

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.source.VarField
import uk.ac.wellcome.platform.transformer.sierra.transformers.SierraAgents
import uk.ac.wellcome.sierra_adapter.model.SierraBibNumber

// Populate wwork:subject
//
// Use MARC field "611".
//
// *  Populate the platform "label" with the concatenated values of
//    subfields a, c, and d.
//
// *  Populate "concepts" with a single value:
//
//    -   Create "label" from subfields a, c and d
//    -   Set "type" to "Meeting"
//    -   Use subfield 0 to populate "identifiers", if present.  Note the
//        identifierType should be "lc-names".
//
// https://www.loc.gov/marc/bibliographic/bd611.html
//
object SierraMeetingSubjects
    extends SierraSubjectsTransformer
    with SierraAgents {

  val subjectVarFields = List("611")

  val labelSubfields = List("a", "b", "c")

  def getSubjectsFromVarFields(bibId: SierraBibNumber,
                               varFields: List[VarField]) =
    varFields.flatMap { varField =>
      createLabel(varField, subfieldTags = List("a", "c", "d")) match {
        case "" => None
        case label =>
          val subject = Subject(
            label = label,
            concepts = List(Meeting(label = label))
          )
          Some(
            varField.indicator2 match {
              case Some("0") =>
                subject.copy(id = identify(varField.subfields, "Meeting"))
              case _ => subject
            }
          )
      }
    }
}
