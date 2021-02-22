package weco.catalogue.sierra_adapter.linker

import grizzled.slf4j.Logging
import uk.ac.wellcome.sierra_adapter.model.{SierraBibNumber, SierraItemRecord, SierraTypedRecordNumber}

import java.time.Instant

case class LinkingRecord(
  bibIds: List[SierraBibNumber],
  unlinkedBibIds: List[SierraBibNumber],
  modifiedDate: Instant
) extends Logging {

  protected def update(
    id: SierraTypedRecordNumber,
    newBibIds: List[SierraBibNumber],
    newModifiedDate: Instant
  ): Option[LinkingRecord] =
    if (modifiedDate.isBefore(newModifiedDate) || modifiedDate == newModifiedDate) {
      Some(
        LinkingRecord(
          modifiedDate = newModifiedDate,
          bibIds = newBibIds,
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
            addList(unlinkedBibIds, bibIds),
            newBibIds
          ),
        )
      )
    } else {
      // We only discard the update if it is strictly older than the stored link.
      //
      // This ensures the linker is idempotent -- if for some reason we're unable to send
      // a message after it gets stored, we'll re-send it if we re-receive the update.
      assert(modifiedDate.isAfter(newModifiedDate))
      warn(
        s"Discarding update to record $id; updated date ($newModifiedDate) is older than existing link ($modifiedDate)"
      )
      None
    }

  def update(itemRecord: SierraItemRecord): Option[LinkingRecord] =
    update(
      id = itemRecord.id,
      newBibIds = itemRecord.bibIds,
      newModifiedDate = itemRecord.modifiedDate
    )

  private def addList[T](x: List[T], y: List[T]): List[T] =
    (x.toSet ++ y.toSet).toList

  private def subList[T](x: List[T], y: List[T]): List[T] =
    (x.toSet -- y.toSet).toList
}

case object LinkingRecord {
  def apply(record: SierraItemRecord): LinkingRecord =
    LinkingRecord(
      bibIds = record.bibIds,
      unlinkedBibIds = record.unlinkedBibIds,
      modifiedDate = record.modifiedDate
    )
}
