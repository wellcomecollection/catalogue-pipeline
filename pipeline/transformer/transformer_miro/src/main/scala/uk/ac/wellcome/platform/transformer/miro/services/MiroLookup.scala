package uk.ac.wellcome.platform.transformer.miro.services

import uk.ac.wellcome.platform.transformer.miro.models.{MiroMetadata, MiroVHSRecord}
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.storage.ReadError
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.Readable

class MiroLookup(
  miroIndexStore: Readable[String, MiroVHSRecord],
  typedStore: Readable[S3ObjectLocation, MiroRecord]
) {
  def lookupRecord(id: String): Either[ReadError, (MiroRecord, MiroMetadata, Int)] =
    for {
      indexRecord <- miroIndexStore.get(id)
      miroMetadata = indexRecord.identifiedT.toMiroMetadata
      version = indexRecord.identifiedT.version

      typedStoreRecord <- typedStore.get(indexRecord.identifiedT.location)
      miroRecord = typedStoreRecord.identifiedT

      result = (miroRecord, miroMetadata, version)
    } yield result
}
