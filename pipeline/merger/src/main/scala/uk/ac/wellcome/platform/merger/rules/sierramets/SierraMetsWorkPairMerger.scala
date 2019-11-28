package uk.ac.wellcome.platform.merger.rules.sierramets

import uk.ac.wellcome.models.work.internal.{
  DigitalLocation,
  IdentifiableRedirect,
  Item,
  MaybeDisplayable,
  TransformedBaseWork,
  Unidentifiable,
  UnidentifiedRedirectedWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.model.MergedWork
import uk.ac.wellcome.platform.merger.rules.{MergerRule, WorkPairMerger}

/**
  * A METS work contains information relating to the digitisation of a Sierra work.
  * Currently that amounts to a digital location on the item which contains
  * the url to the iiif-presentation API and the license associated with it.
  *
  * If we have a Sierra and a METS work each one with a single item,
  * we merge the digital location of the METS work into the locations of the Sierra work.
  *
  * If the Sierra work already contains a digital location for the same URL, we take
  * the one coming from the METS work because the Sierra one doesn't have a `License` as license
  * information for digitised works is only available in the METS XML.
  */
trait SierraMetsWorkPairMerger extends WorkPairMerger {
  override def mergeAndRedirectWorkPair(
    sierraWork: UnidentifiedWork,
    metsWork: TransformedBaseWork): Option[MergedWork] = {
    (sierraWork.data.items, metsWork.data.items) match {
      case (List(sierraItem), List(metsItem: Unidentifiable[Item])) =>
        metsItem.agent.locations match {
          case List(metsLocation: DigitalLocation) =>
            val targetWork = sierraWork.withData(data =>
              data.copy(items = List(mergeLocations(sierraItem, metsLocation))))
            val redirectedWork = UnidentifiedRedirectedWork(
              metsWork.sourceIdentifier,
              metsWork.version,
              IdentifiableRedirect(sierraWork.sourceIdentifier))
            Some(MergedWork(targetWork, redirectedWork))
          case _ => None
        }
      case _ => None
    }
  }

  private def mergeLocations(sierraItem: MaybeDisplayable[Item],
                             metsLocation: DigitalLocation) = {
    sierraItem.withAgent(item => {
      val filteredLocations = item.locations.filter {
        case l: DigitalLocation if l.url.equals(metsLocation.url) => false
        case _                                                    => true
      }
      item.copy(locations = filteredLocations :+ metsLocation)
    })
  }
}

object SierraMetsMergerRule
    extends MergerRule
    with SierraMetsPartitioner
    with SierraMetsWorkPairMerger
