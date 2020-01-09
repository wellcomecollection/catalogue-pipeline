package uk.ac.wellcome.platform.merger.rules.physicaldigital

import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.model.MergedWork
import uk.ac.wellcome.platform.merger.rules.{MergerRule, WorkPairMerger}

/** If we have a pair of Sierra records:
  *
  *   - One of which has a at least one item containing a Physical location ("physical work")
  *   - The other of which has a single Digital location ("digital work")
  *
  * Then the digital work is a digitised version of the physical work.
  *   - we merge the items from both works into the physical work
  *   - we merge the identifiers from both into the physical work
  *   - we redirect the digital work to the physical work.
  *
  */
trait SierraPhysicalDigitalWorkPairMerger
    extends WorkPairMerger
    with Logging
    with MergerLogging {
  override def mergeAndRedirectWorkPair(
    physicalWork: UnidentifiedWork,
    digitalWork: TransformedBaseWork): Option[MergedWork] =
    (physicalWork.data.items, digitalWork.data.items) match {
      case (physicalItems @ _ :: _, List(digitalItem: Unidentifiable[Item])) =>
        info(
          s"Merging ${describeWorkPair(physicalWork, digitalWork)} work pair.")

        val mergedWork = physicalWork.copy(
          data = physicalWork.data.copy(
            otherIdentifiers = physicalWork.data.otherIdentifiers ++ digitalWork.identifiers,
            items = mergeItems(physicalItems, digitalItem)
          )
        )

        Some(
          MergedWork(
            mergedWork,
            UnidentifiedRedirectedWork(
              version = digitalWork.version,
              sourceIdentifier = digitalWork.sourceIdentifier,
              redirect = IdentifiableRedirect(physicalWork.sourceIdentifier),
            )
          ))
      case _ =>
        debug(
          s"Not merging physical ${describeWorkPairWithItems(physicalWork, digitalWork)} due to item counts.")
        None
    }

  // If the physical work has a single item, we merge the items by appending
  // the digital location to the locations of the item on the physical work.
  // If the physical work has more than one item, we append the digital item
  // to the list of items on the physical work.
  private def mergeItems(physicalItems: List[MaybeDisplayable[Item]],
                         digitalItem: Unidentifiable[Item]) = {
    physicalItems match {
      case List(physicalItem: Identifiable[Item]) =>
        List(
          physicalItem.copy(
            thing = physicalItem.thing.copy(
              locations = physicalItem.thing.locations ++ digitalItem.thing.locations
            )
          )
        )
      case _ => physicalItems :+ digitalItem
    }
  }
}

object SierraPhysicalDigitalMergeRule
    extends MergerRule
    with SierraPhysicalDigitalWorkPairMerger
    with SierraPhysicalDigitalPartitioner
