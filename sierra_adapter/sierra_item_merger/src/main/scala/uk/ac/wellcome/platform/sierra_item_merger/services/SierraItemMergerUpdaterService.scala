package uk.ac.wellcome.platform.sierra_item_merger.services

import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.transformable.sierra.SierraItemRecord
import uk.ac.wellcome.platform.sierra_item_merger.exceptions.SierraItemMergerException
import uk.ac.wellcome.platform.sierra_item_merger.links.{ItemLinker, ItemUnlinker}
import uk.ac.wellcome.storage.vhs.{EmptyMetadata, Entry, VersionedHybridStore}

import scala.concurrent.{ExecutionContext, Future}

class SierraItemMergerUpdaterService(
  vhs: VersionedHybridStore[String, SierraTransformable, EmptyMetadata]
)(implicit ec: ExecutionContext) {

  def update(itemRecord: SierraItemRecord): Future[Seq[Entry[String, EmptyMetadata]]] = {
    val mergeUpdates = itemRecord.bibIds.map { bibId =>
      vhs
        .update(id = bibId.withoutCheckDigit)(
          ifNotExisting = (
            SierraTransformable(
              sierraId = bibId,
              itemRecords = Map(itemRecord.id -> itemRecord)),
            EmptyMetadata()))(
          ifExisting = (existingTransformable, existingMetadata) => {
            (
              ItemLinker.linkItemRecord(existingTransformable, itemRecord),
              existingMetadata)
          })
    }

    val unlinkUpdates =
      itemRecord.unlinkedBibIds.map { unlinkedBibId =>
        vhs
          .update(id = unlinkedBibId.withoutCheckDigit)(
            ifNotExisting = throw SierraItemMergerException(
              s"Missing Bib record to unlink: $unlinkedBibId")
          )(
            ifExisting = (existingTransformable, existingMetadata) =>
              (
                ItemUnlinker
                  .unlinkItemRecord(existingTransformable, itemRecord),
                existingMetadata)
          )
      }

    val futures = (mergeUpdates ++ unlinkUpdates).map {
      case Right(entry) => Future.successful(entry)
      case Left(storageError) => Future.failed(storageError.e)
    }

    Future.sequence(futures)
  }
}
