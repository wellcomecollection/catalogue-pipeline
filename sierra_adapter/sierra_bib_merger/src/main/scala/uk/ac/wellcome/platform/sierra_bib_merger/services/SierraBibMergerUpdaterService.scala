package uk.ac.wellcome.platform.sierra_bib_merger.services

import uk.ac.wellcome.platform.sierra_bib_merger.merger.BibMerger
import uk.ac.wellcome.sierra_adapter.model.{
  SierraBibRecord,
  SierraTransformable
}
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.store.VersionedStore

class SierraBibMergerUpdaterService(
  versionedHybridStore: VersionedStore[String, Int, SierraTransformable]
) {

  def update(
    bibRecord: SierraBibRecord): Either[Throwable, Version[String, Int]] =
    versionedHybridStore
      .upsert(bibRecord.id.withoutCheckDigit)(SierraTransformable(bibRecord)) {
        existingSierraTransformable =>
          Right(
            BibMerger.mergeBibRecord(existingSierraTransformable, bibRecord))
      }
      .map { id =>
        id.id
      }
      .left
      .map(err => err.e)

}
