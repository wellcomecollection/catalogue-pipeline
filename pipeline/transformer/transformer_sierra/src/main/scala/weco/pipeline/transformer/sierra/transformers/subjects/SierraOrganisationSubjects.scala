package weco.pipeline.transformer.sierra.transformers.subjects

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{Organisation, Subject}
import weco.pipeline.transformer.sierra.exceptions.CataloguingException
import weco.pipeline.transformer.sierra.transformers.SierraAgents
import weco.sierra.models.identifiers.SierraBibNumber
import weco.sierra.models.marc.VarField

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

      Subject(
        label = label,
        concepts = List(organisation),
        id = identify(varField, "Subject")
      )
    }

  private def createOrganisation(
    bibId: SierraBibNumber,
    varField: VarField): Organisation[IdState.Unminted] = {
    val label = createLabel(varField, subfieldTags = List("a", "b"))

    // @@AWLC: I'm not sure if this can happen in practice -- but we don't have
    // enough information to build the Organisation, so erroring out here is
    // the best we can do for now.
    if (label == "") {
      throw CataloguingException(
        bibId,
        s"Not enough information to build a label on $varField")
    }

    Organisation(
      label = label,
      id =
        identify(varfield = varField, ontologyType = "Organisation"))
  }
}
