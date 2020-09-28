package uk.ac.wellcome.platform.merger.models

import uk.ac.wellcome.models.work.internal._
import WorkState.Source

object Sources {
  def findFirstLinkedDigitisedSierraWorkFor(
    target: Work.Visible[Source],
    sources: Seq[Work[Source]]): Option[Work[Source]] = {

    val digitisedLinkedIds = target.data.mergeCandidates
      .filter(_.reason.contains("Physical/digitised Sierra work"))
      .map(_.identifier)

    sources.find(source => digitisedLinkedIds.contains(source.sourceIdentifier))
  }
}
