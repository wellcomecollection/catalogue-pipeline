package uk.ac.wellcome.platform.merger.models

import uk.ac.wellcome.models.work.internal.WorkState.Identified
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.rules.WorkPredicates

object Sources {
  import WorkPredicates._

  def findFirstLinkedDigitisedSierraWorkFor(
    target: Work.Visible[Identified],
    sources: Seq[Work[Identified]]): Option[Work[Identified]] =
    if (physicalSierra(target)) {
      val digitisedLinkedIds = target.data.mergeCandidates
        .filter(_.reason.contains("Physical/digitised Sierra work"))
        .map(_.id.canonicalId)

      sources.find(source =>
        digitisedLinkedIds.contains(source.state.canonicalId))
    } else {
      None
    }
}
