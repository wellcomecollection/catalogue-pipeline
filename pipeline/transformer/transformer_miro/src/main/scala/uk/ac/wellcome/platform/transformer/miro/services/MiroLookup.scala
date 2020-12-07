package uk.ac.wellcome.platform.transformer.miro.services

import uk.ac.wellcome.platform.transformer.miro.models.{
  MiroMetadata,
  MiroVHSRecord
}
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.storage.{Identified, ReadError, Version}
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.Readable

class MiroLookup(
  miroVhsReader: Readable[String, MiroVHSRecord],
  typedStore: Readable[S3ObjectLocation, MiroRecord]
) {
  def lookupRecord(
    id: String): Either[ReadError, Identified[Version[String, Int], (MiroRecord, MiroMetadata)]] =
    for {
      vhsRecord <- miroVhsReader.get(id)
      miroMetadata = vhsRecord.identifiedT.toMiroMetadata
      version = vhsRecord.identifiedT.version

      typedStoreRecord <- typedStore.get(vhsRecord.identifiedT.location)
      miroRecord = typedStoreRecord.identifiedT

      result = (miroRecord, miroMetadata)
    } yield Identified(Version(vhsRecord.id, version), result)
}
