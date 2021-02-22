package uk.ac.wellcome.platform.sierra_items_to_dynamo.services

import uk.ac.wellcome.platform.sierra_items_to_dynamo.merger.SierraItemRecordMerger
import uk.ac.wellcome.sierra_adapter.model.{SierraItemNumber, SierraItemRecord}
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.{Identified, UpdateNotApplied}
import weco.catalogue.sierra_adapter.linker.LinkingRecord

class SierraItemLinkStore(
  store: VersionedStore[SierraItemNumber, Int, LinkingRecord]) {
  def update(newRecord: SierraItemRecord)
    : Either[Throwable, Option[SierraItemRecord]] = {
    val newLink = LinkingRecord(newRecord)

    val upsertResult: store.UpdateEither =
      store.upsert(newRecord.id)(newLink) {
        SierraItemRecordMerger.mergeItems(_, newRecord) match {
          case Some(updatedLink) => Right(updatedLink)
          case None =>
            Left(
              UpdateNotApplied(
                new Throwable(s"Item ${newRecord.id} is already up-to-date")))
        }
      }

    upsertResult match {
      case Right(Identified(_, updatedLink)) =>
        val updatedRecord = newRecord.copy(
          unlinkedBibIds = updatedLink.unlinkedBibIds
        )
        Right(Some(updatedRecord))

      case Left(_: UpdateNotApplied) => Right(None)
      case Left(err)                 => Left(err.e)
    }
  }
}
