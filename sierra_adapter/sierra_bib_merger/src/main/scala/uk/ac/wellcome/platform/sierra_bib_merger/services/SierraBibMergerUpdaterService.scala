package uk.ac.wellcome.platform.sierra_bib_merger.services

import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.transformable.sierra.SierraBibRecord
import uk.ac.wellcome.platform.sierra_bib_merger.merger.BibMerger
import uk.ac.wellcome.storage.StorageError
import uk.ac.wellcome.storage.vhs.{EmptyMetadata, Entry, VersionedHybridStore}

class SierraBibMergerUpdaterService(
  vhs: VersionedHybridStore[String, SierraTransformable, EmptyMetadata]
) {

  def update(bibRecord: SierraBibRecord)
    : Either[StorageError, Entry[String, EmptyMetadata]] =
    vhs
      .update(id = bibRecord.id.withoutCheckDigit)(
        ifNotExisting = (SierraTransformable(bibRecord), EmptyMetadata()))(
        ifExisting = (existingSierraTransformable, existingMetadata) => {
          (
            BibMerger.mergeBibRecord(existingSierraTransformable, bibRecord),
            existingMetadata)
        }
      )
}
