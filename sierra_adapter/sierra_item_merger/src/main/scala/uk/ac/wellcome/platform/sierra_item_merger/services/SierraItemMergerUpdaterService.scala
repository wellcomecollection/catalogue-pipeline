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
  StorageError,
  UpdateNotApplied,
  Version
}
import cats.implicits._
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import weco.catalogue.source_model.store.SourceVHS

class SierraItemMergerUpdaterService(
  sourceVHS: SourceVHS[SierraTransformable]
) {

  def update(itemRecord: SierraItemRecord)
    : Either[StorageError,
             List[Identified[Version[String, Int], S3ObjectLocation]]] = {
    val linkUpdates =
      itemRecord.bibIds.map { linkBib(_, itemRecord) }

    val unlinkUpdates =
      itemRecord.unlinkedBibIds.map { unlinkBib(_, itemRecord) }

    (linkUpdates ++ unlinkUpdates).filter {
      case Left(_: UpdateNotApplied) => false
      case _                         => true
    }.sequence
  }

  private def linkBib(bibId: SierraBibNumber,
                      itemRecord: SierraItemRecord): Either[
    StorageError,
    Identified[Version[String, Int], S3ObjectLocation]] = {
    val newTransformable =
      SierraTransformable(
        sierraId = bibId,
        itemRecords = Map(itemRecord.id -> itemRecord)
      )

    sourceVHS
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
      .map { case Identified(id, (location, _)) => Identified(id, location) }
  }

  private def unlinkBib(unlinkedBibId: SierraBibNumber,
                        itemRecord: SierraItemRecord)
    : Either[StorageError, Identified[Version[String, Int], S3ObjectLocation]] =
    sourceVHS
      .update(unlinkedBibId.withoutCheckDigit) { existingTransformable =>
        ItemUnlinker.unlinkItemRecord(existingTransformable, itemRecord) match {
          case Some(updatedRecord) => Right(updatedRecord)
          case None =>
            Left(
              UpdateNotApplied(
                new Throwable(s"Bib $unlinkedBibId is already up to date")))
        }
      }
      .map { case Identified(id, (location, _)) => Identified(id, location) }
}
