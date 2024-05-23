package weco.pipeline.matcher.models

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.{Work, WorkState}

/** This is essentially Work[WorkState.Identified], but without the associated
  * WorkData, which isn't used by the matcher. In theory you should be able to
  * replace most uses of this case class with a Work[Identified] and the code
  * would compile unmodified -- this just means we have to fetch less from
  * Elasticsearch.
  */
case class WorkStub(
  state: WorkState.Identified,
  version: Int,
  @JsonKey("type") workType: String
) {
  lazy val id: CanonicalId = state.canonicalId

  lazy val mergeCandidateIds: Set[CanonicalId] =
    state.mergeCandidates
      .map {
        mergeCandidate =>
          mergeCandidate.id.canonicalId
      }
      // TODO: Do we need this filterNot?  Will a work ever refer to itself?
      .filterNot { _ == id }
      .toSet

  lazy val ids: Set[CanonicalId] = mergeCandidateIds + id

  lazy val suppressed: Boolean = workType == "Deleted"
}

case object WorkStub {
  def apply(work: Work[Identified]): WorkStub = {
    val workType = work match {
      case _: Work.Visible[Identified]    => "Visible"
      case _: Work.Invisible[Identified]  => "Invisible"
      case _: Work.Deleted[Identified]    => "Deleted"
      case _: Work.Redirected[Identified] => "Redirected"
    }

    WorkStub(
      state = work.state,
      version = work.version,
      workType = workType
    )
  }
}
