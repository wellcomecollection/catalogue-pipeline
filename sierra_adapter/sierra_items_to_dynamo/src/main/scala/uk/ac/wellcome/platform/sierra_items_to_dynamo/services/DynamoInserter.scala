package uk.ac.wellcome.platform.sierra_items_to_dynamo.services

import uk.ac.wellcome.platform.sierra_items_to_dynamo.merger.SierraItemRecordMerger
import uk.ac.wellcome.sierra_adapter.model.SierraItemRecord
import uk.ac.wellcome.storage.{Identified, UpdateNotApplied, Version}
import weco.catalogue.source_model.store.SourceVHS

class DynamoInserter(sourceVHS: SourceVHS[SierraItemRecord]) {
  def insertIntoDynamo(updatedRecord: SierraItemRecord)
    : Either[Throwable, Option[Version[String, Int]]] = {
    val upsertResult =
      sourceVHS
        .upsert(updatedRecord.id.withoutCheckDigit)(updatedRecord) {
          SierraItemRecordMerger.mergeItems(_, updatedRecord) match {
            case Some(mergedRecord) => Right(mergedRecord)
            case None =>
              Left(
                UpdateNotApplied(new Throwable(
                  s"Item ${updatedRecord.id} is already up-to-date")))
          }
        }

    upsertResult match {
      case Right(Identified(id, _))  => Right(Some(id))
      case Left(_: UpdateNotApplied) => Right(None)
      case Left(err)                 => Left(err.e)
    }
  }
}
