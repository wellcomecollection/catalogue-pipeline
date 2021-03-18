package uk.ac.wellcome.platform.matcher.models

import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.Identified

case class WorkLinks(workId: String,
                     version: Int,
                     referencedWorkIds: Set[String]) {
  lazy val ids: Set[String] = referencedWorkIds + workId
}

case object WorkLinks {
  def apply(work: Work[Identified]): WorkLinks = {
    val id = work.id
    val referencedWorkIds = work.data.mergeCandidates
      .map { mergeCandidate =>
        mergeCandidate.id.canonicalId
      }
      .filterNot { _ == id }
      .toSet

    WorkLinks(id, work.version, referencedWorkIds)
  }
}
