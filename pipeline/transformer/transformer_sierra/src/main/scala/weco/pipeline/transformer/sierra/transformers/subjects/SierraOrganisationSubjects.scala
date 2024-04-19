package weco.pipeline.transformer.sierra.transformers.subjects

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.AbstractRootConcept
import weco.pipeline.transformer.marc_common.models.MarcField
import weco.pipeline.transformer.marc_common.transformers.MarcOrganisation

import scala.util.{Failure, Success, Try}

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
object SierraOrganisationSubjects extends SierraSubjectsTransformer {
  override protected val labelSubfields: Seq[String] =
    Seq("a", "b", "c", "d", "e")
  override protected val subjectVarFields: List[String] = List("610")
  override protected val ontologyType: String = "Organisation"

  protected object OrganisationAsSubjectConcept extends MarcOrganisation {
    override protected val labelSubfieldTags: Seq[String] = Seq("a", "b")
  }

  override def getSubjectConcepts(
    field: MarcField
  ): Try[Seq[AbstractRootConcept[IdState.Unminted]]] =
    OrganisationAsSubjectConcept(field) match {
      case Success(organisation) => Success(Seq(organisation))
      case Failure(exception)    => Failure(exception)
    }

}
