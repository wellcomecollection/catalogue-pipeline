package uk.ac.wellcome.platform.matcher.models

import uk.ac.wellcome.models.work.internal._
import WorkState.Identified

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
