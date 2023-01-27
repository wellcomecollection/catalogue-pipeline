package weco.pipeline.transformer.calm.services

import weco.catalogue.source_model.CalmSourcePayload
import weco.catalogue.source_model.calm.CalmRecord
import weco.pipeline.transformer.SourceDataRetriever
import weco.pipeline.transformer.calm.models.CalmSourceData
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.Readable
import weco.storage.{Identified, ReadError, Version}

class CalmSourceDataRetriever(
  recordReadable: Readable[S3ObjectLocation, CalmRecord]
) extends SourceDataRetriever[CalmSourcePayload, CalmSourceData] {

  override def lookupSourceData(
    p: CalmSourcePayload
  ): Either[ReadError, Identified[Version[String, Int], CalmSourceData]] =
    recordReadable
      .get(p.location)
      .map { case Identified(_, record) =>
        Identified(
          Version(p.id, p.version),
          CalmSourceData(
            record = record,
            isDeleted = p.isDeleted
          )
        )
      }
}
