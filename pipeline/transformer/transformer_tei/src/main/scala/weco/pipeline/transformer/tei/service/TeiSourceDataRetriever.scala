package weco.pipeline.transformer.tei.service

import weco.catalogue.source_model.TeiSourcePayload
import weco.catalogue.source_model.tei.TeiMetadata
import weco.pipeline.transformer.SourceDataRetriever
import weco.storage.{Identified, ReadError, Version}

class TeiSourceDataRetriever extends SourceDataRetriever[TeiSourcePayload, TeiMetadata] {

  override def lookupSourceData(
    payload: TeiSourcePayload
  ): Either[ReadError, Identified[Version[String, Int], TeiMetadata]] =
    Right(Identified(Version(payload.id, payload.version), payload.metadata))
}
