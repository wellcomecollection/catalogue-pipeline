package uk.ac.wellcome.platform.merger.rules.sierramets

import uk.ac.wellcome.models.work.internal.{DigitalLocation, IdentifiableRedirect, TransformedBaseWork, UnidentifiedRedirectedWork, UnidentifiedWork}
import uk.ac.wellcome.platform.merger.model.MergedWork
import uk.ac.wellcome.platform.merger.rules.WorkPairMerger

object SierraMetsWorkPairMerger extends WorkPairMerger {
  override def mergeAndRedirectWorkPair(sierraWork: UnidentifiedWork, metsWork: TransformedBaseWork): Option[MergedWork] = {
    (sierraWork.data.items, metsWork.data.items) match {
      case (List(sierraItem),List(metsItem)) =>
        metsItem.agent.locations match {
          case List(metsLocation: DigitalLocation) =>
        val mergedItem = sierraItem.withAgent (i => {
          val filteredLocations = i.locations.filter{
            case l: DigitalLocation if l.url.equals(metsLocation.url) => false
            case _ => true
          }

          i.copy(locations = filteredLocations :+ metsLocation)
        } )

        Some (MergedWork (sierraWork.withData (data => data.copy (items = List (mergedItem) ) ), UnidentifiedRedirectedWork (metsWork.sourceIdentifier, metsWork.version, IdentifiableRedirect (sierraWork.sourceIdentifier) ) ) )
          case _ => None
        }
      case _ => None
    }
  }
}
