package weco.pipeline.sierra_linker.models

import grizzled.slf4j.Logging
import weco.catalogue.source_model.sierra.{
  AbstractSierraRecord,
  SierraHoldingsRecord,
  SierraItemRecord,
  SierraOrderRecord
}
import weco.sierra.models.identifiers.SierraBibNumber

trait LinkOps[SierraRecord <: AbstractSierraRecord[_]] extends Logging {
  def getBibIds(r: SierraRecord): List[SierraBibNumber]

  def createLink(r: SierraRecord): Link

  def copyUnlinkedBibIds(link: Link, targetRecord: SierraRecord): SierraRecord

  def updateLink(existingLink: Link, newRecord: SierraRecord): Option[Link] =
    if (
      existingLink.modifiedDate.isBefore(newRecord.modifiedDate) ||
      existingLink.modifiedDate == newRecord.modifiedDate
    ) {
      Some(
        Link(
          modifiedDate = newRecord.modifiedDate,
          bibIds = getBibIds(newRecord),
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
            getBibIds(newRecord)
          )
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

object LinkOps {
  implicit val itemLinksOps = new LinkOps[SierraItemRecord] {
    override def getBibIds(
      itemRecord: SierraItemRecord
    ): List[SierraBibNumber] =
      itemRecord.bibIds

    override def createLink(itemRecord: SierraItemRecord): Link =
      Link(itemRecord)

    override def copyUnlinkedBibIds(
      link: Link,
      itemRecord: SierraItemRecord
    ): SierraItemRecord =
      itemRecord.copy(unlinkedBibIds = link.unlinkedBibIds)
  }

  implicit val holdingsLinkOps = new LinkOps[SierraHoldingsRecord] {
    override def getBibIds(
      holdingsRecord: SierraHoldingsRecord
    ): List[SierraBibNumber] =
      holdingsRecord.bibIds

    override def createLink(holdingsRecord: SierraHoldingsRecord): Link =
      Link(holdingsRecord)

    override def copyUnlinkedBibIds(
      link: Link,
      holdingsRecord: SierraHoldingsRecord
    ): SierraHoldingsRecord =
      holdingsRecord.copy(unlinkedBibIds = link.unlinkedBibIds)
  }

  implicit val orderLinkOps = new LinkOps[SierraOrderRecord] {
    override def getBibIds(
      orderRecord: SierraOrderRecord
    ): List[SierraBibNumber] =
      orderRecord.bibIds

    override def createLink(orderRecord: SierraOrderRecord): Link =
      Link(orderRecord)

    override def copyUnlinkedBibIds(
      link: Link,
      orderRecord: SierraOrderRecord
    ): SierraOrderRecord =
      orderRecord.copy(unlinkedBibIds = link.unlinkedBibIds)
  }
}
