package uk.ac.wellcome.platform.sierra_item_merger.services

import uk.ac.wellcome.platform.sierra_item_merger.links.{
  ItemLinker,
  ItemUnlinker
}
import uk.ac.wellcome.sierra_adapter.model.{
  SierraItemRecord,
  SierraTransformable
}
import uk.ac.wellcome.storage.{UpdateError, Version}
import uk.ac.wellcome.storage.store.VersionedStore
import cats.implicits._

class SierraItemMergerUpdaterService(
  versionedHybridStore: VersionedStore[String, Int, SierraTransformable]
) {

  def update(itemRecord: SierraItemRecord)
    : Either[UpdateError, List[Version[String, Int]]] = {
    val mergeUpdates: List[Either[UpdateError, Version[String, Int]]] =
      itemRecord.bibIds.map { bibId =>
        versionedHybridStore
          .upsert(bibId.withoutCheckDigit)(
            SierraTransformable(
              sierraId = bibId,
              itemRecords = Map(itemRecord.id -> itemRecord))) {
            existingTransformable =>
              Right(
                ItemLinker.linkItemRecord(existingTransformable, itemRecord))
          }
          .map(id => id.id)

      }

    val unlinkUpdates: List[Either[UpdateError, Version[String, Int]]] =
      itemRecord.unlinkedBibIds.map { unlinkedBibId =>
        versionedHybridStore
          .update(unlinkedBibId.withoutCheckDigit) { existingTransformable =>
            Right(
              ItemUnlinker
                .unlinkItemRecord(existingTransformable, itemRecord))
          }
          .map(id => id.id)
      }

    (mergeUpdates ++ unlinkUpdates).sequence
  }
}
