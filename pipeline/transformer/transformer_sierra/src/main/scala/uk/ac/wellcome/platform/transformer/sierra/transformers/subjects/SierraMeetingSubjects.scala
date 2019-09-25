package uk.ac.wellcome.platform.transformer.sierra.transformers.subjects

import uk.ac.wellcome.models.transformable.sierra.SierraBibNumber
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.source.VarField
import uk.ac.wellcome.platform.transformer.sierra.transformers.SierraAgents

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
object SierraMeetingSubjects extends SierraSubjectsTransformer with SierraAgents {

  val subjectVarFields = List("611")

  def getSubjectsFromVarFields(bibId: SierraBibNumber, varFields: List[VarField]) =
    varFields.map { varField =>

      val label = createLabel(varField, subfieldTags = List("a", "c", "d"))

      val subject = Subject(
        label = label,
        concepts = List(Unidentifiable(Meeting(label = label)))
      )

      varField.indicator2 match {
        case Some("0") => identify(varField.subfields, subject, "Meeting")
        case _         => Unidentifiable(subject)
      }
    }
}
