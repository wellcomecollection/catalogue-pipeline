package uk.ac.wellcome.platform.merger.rules.sierramets

import uk.ac.wellcome.models.work.internal._
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
    metsWork: TransformedBaseWork): Option[MergedWork] =
    (sierraWork.data.items, metsWork.data.items) match {
      case (List(sierraItem), List(metsItem: Unidentifiable[Item])) =>
        metsItem.agent.locations match {
          case List(metsLocation: DigitalLocation) =>
            createMergedWork(sierraWork, metsWork, List(mergeLocations(sierraItem, metsLocation)))
          case _ => None
        }
      case (sierraItems@ _ :: _, List(metsItem)) =>
        createMergedWork(sierraWork, metsWork, sierraItems :+ metsItem)
      case _ => None
    }

  private def createMergedWork(sierraWork: UnidentifiedWork, metsWork: TransformedBaseWork, items: List[MaybeDisplayable[Item]]) = {
    val targetWork = sierraWork.withData { data =>
      data.copy(
        items = items,
        thumbnail =
          metsWork.data.thumbnail.map(Some(_)).getOrElse(data.thumbnail)
      )
    }
    val redirectedWork = UnidentifiedRedirectedWork(
      metsWork.sourceIdentifier,
      metsWork.version,
      IdentifiableRedirect(sierraWork.sourceIdentifier))
    Some(MergedWork(targetWork, redirectedWork))
  }

  private def mergeLocations(sierraItem: MaybeDisplayable[Item],
                             metsLocation: DigitalLocation) =
    sierraItem.withAgent { item =>
      item.copy(
        locations =
          item.locations.filterNot(shouldIgnoreLocation(_, metsLocation.url))
            :+ metsLocation
      )
    }

  private def shouldIgnoreLocation(location: Location, metsUrl: String) =
    location match {
      case DigitalLocation(url, LocationType("iiif-image", _, _), _, _, _) =>
        true
      case DigitalLocation(url, _, _, _, _) if url.equals(metsUrl) => true
      case _                                                       => false
    }
}

object SierraMetsMergerRule
    extends MergerRule
    with SierraMetsPartitioner
    with SierraMetsWorkPairMerger
