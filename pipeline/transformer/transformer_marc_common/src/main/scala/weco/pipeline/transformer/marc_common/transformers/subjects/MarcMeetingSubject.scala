package weco.pipeline.transformer.marc_common.transformers.subjects

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.{AbstractRootConcept, Subject}
import weco.pipeline.transformer.marc_common.models.MarcField
import weco.pipeline.transformer.marc_common.transformers.MarcMeeting

import scala.util.{Failure, Success, Try}

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
trait MarcMeetingSubject extends MarcSubject with ExcludeMeshIds {
  override protected val labelSubfields: Seq[String] =
    Seq("a", "c", "d")
  override protected val ontologyType: String = "Meeting"

  override def getSubjectConcepts(
    field: MarcField
  ): Try[Seq[AbstractRootConcept[IdState.Unminted]]] =
    MeetingAsSubjectConcept(field) match {
      case Success(organisation) => Success(Seq(organisation))
      case Failure(exception)    => Failure(exception)
    }
  private object MeetingAsSubjectConcept
      extends MarcMeeting
      with ExcludeMeshIds {
    override protected val labelSubfieldTags: Seq[String] = Seq("a", "c", "d")
  }

  override def apply(field: MarcField): Option[Subject[IdState.Unminted]] =
    getSubject(field)
}

object MarcMeetingSubject extends MarcMeetingSubject
