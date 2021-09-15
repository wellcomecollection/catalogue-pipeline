package weco.pipeline.matcher.models

import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified

import java.time.Instant

/** This is the matcher's stand-in for a "Work[Identified]".
  *
  * We don't bother fetching an entire Work from pipeline storage because
  * we only need a few fields on it.
  */
case class WorkStub(id: CanonicalId,
                    modifiedTime: Instant,
                    referencedWorkIds: Set[CanonicalId]) {
  lazy val ids: Set[CanonicalId] = referencedWorkIds + id
}

case object WorkStub {
  def apply(work: Work[Identified]): WorkStub = {
    val id = work.state.canonicalId
    val referencedWorkIds = work.data.mergeCandidates
      .map { mergeCandidate =>
        mergeCandidate.id.canonicalId
      }
      .filterNot { _ == id }
      .toSet

    WorkStub(id, work.state.modifiedTime, referencedWorkIds)
  }
}
