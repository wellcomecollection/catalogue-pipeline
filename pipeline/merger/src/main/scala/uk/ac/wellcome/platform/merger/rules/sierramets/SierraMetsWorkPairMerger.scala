package uk.ac.wellcome.platform.merger.rules.sierramets

import uk.ac.wellcome.models.work.internal.{IdentifiableRedirect, TransformedBaseWork, UnidentifiedRedirectedWork, UnidentifiedWork}
import uk.ac.wellcome.platform.merger.model.MergedWork
import uk.ac.wellcome.platform.merger.rules.WorkPairMerger

object SierraMetsWorkPairMerger extends WorkPairMerger {
  override def mergeAndRedirectWorkPair(firstWork: UnidentifiedWork, secondWork: TransformedBaseWork): Option[MergedWork] = {
    val maybeDisplayable = firstWork.data.items.head
    val value = maybeDisplayable.withAgent(item => item.copy(locations = item.locations ++ secondWork.data.items.head.agent.locations))

    Some(MergedWork(firstWork.withData(data => data.copy(items = List(value))), UnidentifiedRedirectedWork(secondWork.sourceIdentifier, secondWork.version, IdentifiableRedirect(firstWork.sourceIdentifier))))
  }
}
