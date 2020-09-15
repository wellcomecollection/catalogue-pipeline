package uk.ac.wellcome.platform.merger.models

import uk.ac.wellcome.models.work.internal._
import WorkState.Unidentified

object Sources {
  def findFirstLinkedDigitisedSierraWorkFor(
    target: Work.Standard[Unidentified],
    sources: Seq[Work[Unidentified]]): Option[Work[Unidentified]] = {

    val digitisedLinkedIds = target.data.mergeCandidates
      .filter(_.reason.contains("Physical/digitised Sierra work"))
      .map(_.identifier)

    sources.find(source => digitisedLinkedIds.contains(source.sourceIdentifier))
  }
}
