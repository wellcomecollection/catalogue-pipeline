package uk.ac.wellcome.platform.sierra_items_to_dynamo.services

import uk.ac.wellcome.platform.sierra_items_to_dynamo.merger.SierraItemRecordMerger
import uk.ac.wellcome.sierra_adapter.model.SierraItemRecord
import uk.ac.wellcome.storage.store.VersionedStore

class DynamoInserter(
  versionedHybridStore: VersionedStore[String, Int, SierraItemRecord]) {
  def insertIntoDynamo(
    record: SierraItemRecord): Either[Throwable, SierraItemRecord] =
    versionedHybridStore.upsert(record.id.withoutCheckDigit)(record) {
      existingRecord: SierraItemRecord =>
        Right(
          SierraItemRecordMerger
            .mergeItems(
              existingRecord = existingRecord,
              updatedRecord = record))
    }.map(id => id.identifiedT).left.map(_.e)
}
