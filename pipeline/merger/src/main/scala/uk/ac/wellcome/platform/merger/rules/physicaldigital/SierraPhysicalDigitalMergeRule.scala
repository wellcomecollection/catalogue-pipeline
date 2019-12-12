package uk.ac.wellcome.platform.merger.rules.physicaldigital

import grizzled.slf4j.Logging
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.merger.logging.MergerLogging
import uk.ac.wellcome.platform.merger.model.MergedWork
import uk.ac.wellcome.platform.merger.rules.{MergerRule, WorkPairMerger}

/** If we have a pair of Sierra records:
  *
  *   - One of which has a single Physical location ("physical work")
  *   - The other of which has a single Digital location ("digital work")
  *
  * Then the digital work is a digitised version of the physical work.
  * The physical work takes precedence, we copy the location from the digital
  * work, and redirect the digital work to the physical work.
  *
  */
trait SierraPhysicalDigitalWorkPairMerger extends WorkPairMerger with Logging with MergerLogging {
  override def mergeAndRedirectWorkPair(
                                         physicalWork: UnidentifiedWork,
                                         digitalWork: TransformedBaseWork): Option[MergedWork] =
    (physicalWork.data.items, digitalWork.data.items) match {
      case (
        physicalItems@_ :: _,
        List(digitalItem: Unidentifiable[Item])) =>
        info(
          s"Merging ${describeWorkPair(physicalWork, digitalWork)} work pair.")

        // Copy the identifiers and locations from the physical work on to
        // the digital work.  We know both works only have a single item,
        // so these locations definitely correspond to this item.
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

  private def mergeItems(physicalItems: List[MaybeDisplayable[Item]],
                         digitalItem: Unidentifiable[Item]) = {
    physicalItems match {
      case List(physicalItem: Identifiable[Item]) =>
        List(
          physicalItem.copy(
            agent = physicalItem.agent.copy(
              locations = physicalItem.agent.locations ++ digitalItem.agent.locations
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
