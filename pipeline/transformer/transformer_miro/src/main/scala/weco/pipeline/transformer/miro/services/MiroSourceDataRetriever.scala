package weco.pipeline.transformer.miro.services

import weco.catalogue.source_model.MiroSourcePayload
import weco.catalogue.source_model.miro.MiroSourceOverrides
import weco.pipeline.transformer.SourceDataRetriever
import weco.pipeline.transformer.miro.models.MiroMetadata
import weco.pipeline.transformer.miro.source.MiroRecord
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.Readable
import weco.storage.{Identified, ReadError, Version}

class MiroSourceDataRetriever(
  miroReadable: Readable[S3ObjectLocation, MiroRecord]
) extends SourceDataRetriever[
      MiroSourcePayload,
      (MiroRecord, MiroSourceOverrides, MiroMetadata)
    ] {

  override def lookupSourceData(
    p: MiroSourcePayload
  ): Either[ReadError, Identified[
    Version[String, Int],
    (MiroRecord, MiroSourceOverrides, MiroMetadata)
  ]] =
    miroReadable
      .get(p.location)
      .map {
        case Identified(_, miroRecord) =>
          Identified(
            Version(p.id, p.version),
            (
              miroRecord,
              p.overrides.getOrElse(MiroSourceOverrides.empty),
              MiroMetadata(isClearedForCatalogueAPI =
                p.isClearedForCatalogueAPI
              )
            )
          )
      }
}
