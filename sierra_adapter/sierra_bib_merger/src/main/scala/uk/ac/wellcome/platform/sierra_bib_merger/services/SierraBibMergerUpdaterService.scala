package uk.ac.wellcome.platform.sierra_bib_merger.services

import uk.ac.wellcome.platform.sierra_bib_merger.merger.BibMerger
import uk.ac.wellcome.sierra_adapter.model.{SierraBibRecord, SierraTransformable}
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.{Identified, UpdateNotApplied, Version}
import weco.catalogue.source_model.store.SourceVHS

class SierraBibMergerUpdaterService(
  sourceVHS: SourceVHS[SierraTransformable]
) {

  def update(bibRecord: SierraBibRecord)
    : Either[Throwable, Option[Identified[Version[String, Int], S3ObjectLocation]]] = {
    val upsertResult =
      sourceVHS
        .upsert(bibRecord.id.withoutCheckDigit)(SierraTransformable(bibRecord)) {
          BibMerger.mergeBibRecord(_, bibRecord) match {
            case Some(updatedTransformable) => Right(updatedTransformable)
            case None =>
              Left(
                UpdateNotApplied(
                  new Throwable(s"${bibRecord.id} is already up-to-date")))
          }
        }

    upsertResult match {
      case Right(Identified(id, (location, _))) => Right(Some(Identified(id, location)))
      case Left(_: UpdateNotApplied)            => Right(None)
      case Left(err)                            => Left(err.e)
    }
  }
}
