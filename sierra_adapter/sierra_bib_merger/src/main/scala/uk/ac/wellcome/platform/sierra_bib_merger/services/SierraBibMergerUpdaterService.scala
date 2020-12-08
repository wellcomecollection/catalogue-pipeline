package uk.ac.wellcome.platform.sierra_bib_merger.services

import uk.ac.wellcome.platform.sierra_bib_merger.merger.BibMerger
import uk.ac.wellcome.sierra_adapter.model.{
  SierraBibRecord,
  SierraTransformable
}
import uk.ac.wellcome.storage.{Identified, UpdateNotApplied, Version}
import uk.ac.wellcome.storage.store.VersionedStore

class SierraBibMergerUpdaterService(
  versionedHybridStore: VersionedStore[String, Int, SierraTransformable]
) {

  def update(
    bibRecord: SierraBibRecord): Either[Throwable, Option[Version[String, Int]]] = {
    val upsertResult =
      versionedHybridStore
        .upsert(bibRecord.id.withoutCheckDigit)(SierraTransformable(bibRecord)) {
          BibMerger.mergeBibRecord(_, bibRecord) match {
            case Some(updatedTransformable) => Right(updatedTransformable)
            case None => Left(UpdateNotApplied(new Throwable(s"${bibRecord.id} is already up-to-date")))
          }
        }

    upsertResult match {
      case Right(Identified(id, _)) => Right(Some(id))
      case Left(_: UpdateNotApplied) => Right(None)
      case Left(err) => Left(err.e)
    }
  }
}
