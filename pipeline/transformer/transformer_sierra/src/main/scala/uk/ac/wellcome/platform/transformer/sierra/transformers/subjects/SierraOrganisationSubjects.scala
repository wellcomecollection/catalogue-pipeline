package uk.ac.wellcome.platform.transformer.sierra.transformers.subjects

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.sierra.exceptions.CataloguingException
import uk.ac.wellcome.platform.transformer.sierra.source.VarField
import uk.ac.wellcome.platform.transformer.sierra.transformers.SierraAgents
import uk.ac.wellcome.sierra_adapter.model.SierraBibNumber

// Populate wwork:subject
//
// Use MARC field "610".
//
// *  Populate the platform "label" with the concatenated values of
//    subfields a, b, c, d and e.
//
// *  Populate "concepts" with a single value:
//
//    -   Create "label" from subfields a and b
//    -   Set "type" to "Organisation"
//    -   Use subfield 0 to populate "identifiers", if present.  Note the
//        identifierType should be "lc-names".
//
// https://www.loc.gov/marc/bibliographic/bd610.html
//
object SierraOrganisationSubjects
    extends SierraSubjectsTransformer
    with SierraAgents {

  val subjectVarFields = List("610")

  def getSubjectsFromVarFields(bibId: SierraBibNumber,
                               varFields: List[VarField]): Output =
    varFields.map { varField =>
      val label =
        createLabel(varField, subfieldTags = List("a", "b", "c", "d", "e"))

      val organisation = createOrganisation(bibId, varField)

      val subject = Subject(
        label = label,
        concepts = List(organisation)
      )

      varField.indicator2 match {
        case Some("0") =>
          subject.copy(id = identify(varField.subfields, "Subject"))
        case _ => subject
      }
    }

  private def createOrganisation(bibId: SierraBibNumber,
                                 varField: VarField): Organisation[Id.Unminted] = {
    val label = createLabel(varField, subfieldTags = List("a", "b"))

    // @@AWLC: I'm not sure if this can happen in practice -- but we don't have
    // enough information to build the Organisation, so erroring out here is
    // the best we can do for now.
    if (label == "") {
      throw CataloguingException(
        bibId,
        s"Not enough information to build a label on $varField")
    }

    Organisation(label = label)
  }
}
