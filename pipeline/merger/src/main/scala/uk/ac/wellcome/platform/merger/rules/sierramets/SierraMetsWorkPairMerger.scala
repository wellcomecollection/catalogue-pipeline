package uk.ac.wellcome.platform.merger.rules.sierramets

import uk.ac.wellcome.models.work.internal.{DigitalLocation, Identifiable, IdentifiableRedirect, Item, Location, TransformedBaseWork, Unidentifiable, UnidentifiedRedirectedWork, UnidentifiedWork}
import uk.ac.wellcome.platform.merger.model.MergedWork
import uk.ac.wellcome.platform.merger.rules.{MergerRule, WorkPairMerger}

trait SierraMetsWorkPairMerger extends WorkPairMerger {
  override def mergeAndRedirectWorkPair(sierraWork: UnidentifiedWork, metsWork: TransformedBaseWork): Option[MergedWork] = {
    (sierraWork.data.items, metsWork.data.items) match {
      case (List(sierraItem: Identifiable[Item]),List(metsItem: Unidentifiable[Item])) =>
        metsItem.agent.locations match {
          case List(metsLocation: DigitalLocation) =>
        val targetWork = sierraWork.withData(data => data.copy(items = List(mergeLocations(sierraItem, metsLocation))))
            val redirectedWork = UnidentifiedRedirectedWork(metsWork.sourceIdentifier, metsWork.version, IdentifiableRedirect(sierraWork.sourceIdentifier))
            Some (MergedWork (targetWork, redirectedWork ) )
          case _ => None
        }
      case _ => None
    }
  }

  private def mergeLocations(sierraItem: Identifiable[Item], metsLocation: DigitalLocation) = {
    sierraItem.withAgent(item => {
      val filteredLocations = item.locations.filter {
        case l: DigitalLocation if l.url.equals(metsLocation.url) => false
        case _ => true
      }
      item.copy(locations = filteredLocations :+ metsLocation)
    })
  }
}

object SierraMetsMergerRule extends MergerRule with SierraMetsPartitioner with SierraMetsWorkPairMerger