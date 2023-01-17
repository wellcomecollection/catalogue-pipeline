package weco.pipeline.transformer.sierra.services

import weco.catalogue.source_model.SierraSourcePayload
import weco.catalogue.source_model.sierra.SierraTransformable
import weco.pipeline.transformer.SourceDataRetriever
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.Readable
import weco.storage.{Identified, ReadError, Version}

class SierraSourceDataRetriever(
  sierraReadable: Readable[S3ObjectLocation, SierraTransformable],
) extends SourceDataRetriever[SierraSourcePayload, SierraTransformable] {

  override def lookupSourceData(p: SierraSourcePayload)
    : Either[ReadError, Identified[Version[String, Int], SierraTransformable]] =
    sierraReadable
      .get(p.location)
      .map {
        case Identified(_, record) =>
          Identified(Version(p.id, p.version), record)
      }
}
