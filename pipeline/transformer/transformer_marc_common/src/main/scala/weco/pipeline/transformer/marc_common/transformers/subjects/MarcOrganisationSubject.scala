package weco.pipeline.transformer.marc_common.transformers.subjects

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{AbstractRootConcept, Subject}
import weco.pipeline.transformer.marc_common.models.MarcField
import weco.pipeline.transformer.marc_common.transformers.MarcOrganisation
import weco.pipeline.transformer.text.TextNormalisation.TextNormalisationOps

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
trait MarcOrganisationSubject extends MarcSubject with OnlyLocIds {
  override protected val labelSubfields: Seq[String] =
    Seq("a", "b", "c", "d", "e")
  override protected val ontologyType: String = "Organisation"

  private object OrganisationAsSubjectConcept
      extends MarcOrganisation
      with OnlyLocIds
      with DiscardMultipleIds {
    override protected val labelSubfieldTags: Seq[String] = Seq("a", "b")
    override protected def normaliseLabel(label: String): String =
      super.normaliseLabel(label).trimTrailingPeriod
  }

  override def getSubjectConcepts(
    field: MarcField
  ): Try[Seq[AbstractRootConcept[IdState.Unminted]]] =
    OrganisationAsSubjectConcept(field) match {
      case Success(organisation) => Success(Seq(organisation))
      case Failure(exception)    => Failure(exception)
    }

  override def apply(field: MarcField): Option[Subject[IdState.Unminted]] =
    getSubject(field)
}
object MarcOrganisationSubject extends MarcOrganisationSubject
