package uk.ac.wellcome.platform.matcher.models

import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified

case class WorkLinks(workId: CanonicalId,
                     version: Int,
                     referencedWorkIds: Set[CanonicalId]) {
  lazy val ids: Set[CanonicalId] = referencedWorkIds + workId
}

case object WorkLinks {
  def apply(work: Work[Identified]): WorkLinks = {
    val id = work.state.canonicalId
    val referencedWorkIds = work.data.mergeCandidates
      .map { mergeCandidate =>
        mergeCandidate.id.canonicalId
      }
      .filterNot { _ == id }
      .toSet

    WorkLinks(id, work.version, referencedWorkIds)
  }
}
