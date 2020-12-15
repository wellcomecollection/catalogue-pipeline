package uk.ac.wellcome.platform.matcher.models

import uk.ac.wellcome.models.work.internal._
import WorkState.Identified

case class WorkUpdate(workId: String,
                      version: Int,
                      referencedWorkIds: Set[String]) {
  lazy val ids: Set[String] = referencedWorkIds + workId
}

case object WorkUpdate {
  def apply(work: Work[Identified]): WorkUpdate = {
    val id = work.id
    val referencedWorkIds = work.data.mergeCandidates
      .map { mergeCandidate =>
        mergeCandidate.id.canonicalId
      }
      .filterNot { _ == id }
      .toSet

    WorkUpdate(id, work.version, referencedWorkIds)
  }
}
