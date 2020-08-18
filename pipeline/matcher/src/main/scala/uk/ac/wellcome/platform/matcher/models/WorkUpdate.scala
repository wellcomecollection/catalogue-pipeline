package uk.ac.wellcome.platform.matcher.models

import uk.ac.wellcome.models.work.internal.TransformedBaseWork

case class WorkUpdate(workId: String,
                      version: Int,
                      referencedWorkIds: Set[String]) {
  lazy val ids: Set[String] = referencedWorkIds + workId
}

case object WorkUpdate {
  def apply(work: TransformedBaseWork): WorkUpdate = {
    val id = work.sourceIdentifier.toString
    val referencedWorkIds = work.data.mergeCandidates
      .map { mergeCandidate =>
        mergeCandidate.identifier.toString
      }
      .filterNot { _ == id }
      .toSet

    WorkUpdate(id, work.version, referencedWorkIds)
  }
}
