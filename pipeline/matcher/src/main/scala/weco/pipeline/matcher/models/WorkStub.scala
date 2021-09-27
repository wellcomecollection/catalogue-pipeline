package weco.pipeline.matcher.models

import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.{Work, WorkState}

/** This is essentially Work[WorkState.Identified], but without the associated
  * WorkData, which isn't used by the matcher.  In theory you should be able to
  * replace most uses of this case class with a Work[Identified] and the code would
  * compile unmodified -- this just means we have to fetch less from Elasticsearch.
  *
  */
case class WorkStub(state: WorkState.Identified, version: Int) {
  lazy val id: CanonicalId = state.canonicalId

  lazy val referencedWorkIds: Set[CanonicalId] =
    state.mergeCandidates
      .map { mergeCandidate =>
        mergeCandidate.id.canonicalId
      }
      .filterNot { _ == id }
      .toSet

  lazy val ids: Set[CanonicalId] = referencedWorkIds + id
}

case object WorkStub {
  def apply(work: Work[Identified]): WorkStub =
    WorkStub(state = work.state, version = work.version)
}

