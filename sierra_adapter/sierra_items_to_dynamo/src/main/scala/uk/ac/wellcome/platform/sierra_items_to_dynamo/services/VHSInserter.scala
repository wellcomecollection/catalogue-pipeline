package uk.ac.wellcome.platform.sierra_items_to_dynamo.services

import uk.ac.wellcome.models.transformable.sierra.SierraItemRecord
import uk.ac.wellcome.platform.sierra_items_to_dynamo.merger.SierraItemRecordMerger
import uk.ac.wellcome.storage.StorageError
import uk.ac.wellcome.storage.vhs.{EmptyMetadata, Entry, VersionedHybridStore}

class VHSInserter(
  vhs: VersionedHybridStore[String, SierraItemRecord, EmptyMetadata]) {
  def insertIntoVhs(
    record: SierraItemRecord): Either[StorageError, Entry[String, EmptyMetadata]] =
    vhs
      .update(
        id = record.id.withoutCheckDigit
      )(
        ifNotExisting = (record, EmptyMetadata())
      )(
        ifExisting = (existingRecord, existingMetadata) =>
          (
            SierraItemRecordMerger
              .mergeItems(
                existingRecord = existingRecord,
                updatedRecord = record),
            existingMetadata
        )
      )
}
