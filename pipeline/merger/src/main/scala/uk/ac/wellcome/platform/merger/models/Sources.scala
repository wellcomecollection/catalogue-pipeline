package uk.ac.wellcome.platform.merger.models

import uk.ac.wellcome.models.work.internal.WorkState.Identified
import uk.ac.wellcome.models.work.internal._

object Sources {
  def findFirstLinkedDigitisedSierraWorkFor(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]): Option[Work[Identified]] = {

    val digitisedLinkedIds = target.data.mergeCandidates
      .filter(_.reason.contains("Physical/digitised Sierra work"))
      .map(_.identifier)

    sources.find(source => digitisedLinkedIds.contains(source.sourceIdentifier))
  }
}
