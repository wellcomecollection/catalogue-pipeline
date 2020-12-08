package uk.ac.wellcome.platform.sierra_item_merger.services

import uk.ac.wellcome.platform.sierra_item_merger.links.{
  ItemLinker,
  ItemUnlinker
}
import uk.ac.wellcome.sierra_adapter.model.{
  SierraBibNumber,
  SierraItemRecord,
  SierraTransformable
}
import uk.ac.wellcome.storage.{
  Identified,
  UpdateError,
  UpdateNotApplied,
  Version
}
import uk.ac.wellcome.storage.store.VersionedStore
import cats.implicits._

class SierraItemMergerUpdaterService(
  versionedHybridStore: VersionedStore[String, Int, SierraTransformable]
) {

  def update(itemRecord: SierraItemRecord)
    : Either[UpdateError, List[Version[String, Int]]] = {
    val linkUpdates: List[Either[UpdateError, Version[String, Int]]] =
      itemRecord.bibIds.map { linkBib(_, itemRecord) }

    val unlinkUpdates: List[Either[UpdateError, Version[String, Int]]] =
      itemRecord.unlinkedBibIds.map { unlinkBib(_, itemRecord) }

    (linkUpdates ++ unlinkUpdates).filter {
      case Left(_: UpdateNotApplied) => false
      case _                         => true
    }.sequence
  }

  private def linkBib(
    bibId: SierraBibNumber,
    itemRecord: SierraItemRecord): Either[UpdateError, Version[String, Int]] = {
    val newTransformable =
      SierraTransformable(
        sierraId = bibId,
        itemRecords = Map(itemRecord.id -> itemRecord)
      )

    versionedHybridStore
      .upsert(bibId.withoutCheckDigit)(newTransformable) {
        existingTransformable =>
          ItemLinker.linkItemRecord(existingTransformable, itemRecord) match {
            case Some(updatedRecord) => Right(updatedRecord)
            case None =>
              Left(
                UpdateNotApplied(
                  new Throwable(s"Bib $bibId is already up to date")))
          }
      }
      .map { case Identified(id, _) => id }
  }

  private def unlinkBib(
    unlinkedBibId: SierraBibNumber,
    itemRecord: SierraItemRecord): Either[UpdateError, Version[String, Int]] =
    versionedHybridStore
      .update(unlinkedBibId.withoutCheckDigit) { existingTransformable =>
        ItemUnlinker.unlinkItemRecord(existingTransformable, itemRecord) match {
          case Some(updatedRecord) => Right(updatedRecord)
          case None =>
            Left(
              UpdateNotApplied(
                new Throwable(s"Bib $unlinkedBibId is already up to date")))
        }
      }
      .map { case Identified(id, _) => id }
}
