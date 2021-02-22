package uk.ac.wellcome.platform.sierra_items_to_dynamo.merger

import grizzled.slf4j.Logging
import uk.ac.wellcome.platform.sierra_items_to_dynamo.models.SierraItemLink
import uk.ac.wellcome.sierra_adapter.model.SierraItemRecord

object SierraItemRecordMerger extends Logging {
  def mergeItems(existingLink: SierraItemLink,
                 newRecord: SierraItemRecord): Option[SierraItemLink] =
    if (existingLink.modifiedDate.isBefore(newRecord.modifiedDate) ||
      existingLink.modifiedDate == newRecord.modifiedDate) {
      Some(
        SierraItemLink(
          modifiedDate = newRecord.modifiedDate,
          bibIds = newRecord.bibIds,
          // Let's suppose we have
          //
          //    oldRecord = (linked = {1, 2, 3}, unlinked = {4, 5})
          //    newRecord = (linked = {3, 4})
          //
          // First we get a list of all the bibIds the oldRecord knew about, in any capacity:
          //
          //    addList(oldRecord.unlinkedBibIds, oldRecord.bibIds) = {1, 2, 3, 4, 5}
          //
          // Any bibIds in this list which are _not_ on the new record should be unlinked.
          //
          //    unlinkedBibIds
          //      = addList(oldRecord.unlinkedBibIds, oldRecord.bibIds) - newRecord.bibIds
          //      = (all bibIds on old record) - (current bibIds on new record)
          //      = {1, 2, 3, 4, 5} - {3, 4}
          //      = {1, 2, 5}
          //
          unlinkedBibIds = subList(
            addList(existingLink.unlinkedBibIds, existingLink.bibIds),
            newRecord.bibIds
          ),
        )
      )
    } else {
      // We only discard the update if it is strictly older than the stored link.
      //
      // This ensures the linker is idempotent -- if for some reason we're unable to send
      // a message after it gets stored, we'll re-send it if we re-receive the update.
      assert(existingLink.modifiedDate.isAfter(newRecord.modifiedDate))
      warn(
        s"Discarding update to record ${newRecord.id}; updated date (${newRecord.modifiedDate}) is older than existing link (${existingLink.modifiedDate})"
      )
      None
    }

  private def addList[T](x: List[T], y: List[T]): List[T] =
    (x.toSet ++ y.toSet).toList

  private def subList[T](x: List[T], y: List[T]): List[T] =
    (x.toSet -- y.toSet).toList
}
