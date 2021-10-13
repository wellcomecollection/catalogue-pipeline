package weco.pipeline.transformer.mets.services

import weco.catalogue.source_model.MetsSourcePayload
import weco.catalogue.source_model.mets.MetsSourceData
import weco.pipeline.transformer.SourceDataRetriever
import weco.storage.{Identified, ReadError, Version}

class MetsSourceDataRetriever
    extends SourceDataRetriever[MetsSourcePayload, MetsSourceData] {

  override def lookupSourceData(p: MetsSourcePayload)
    : Either[ReadError, Identified[Version[String, Int], MetsSourceData]] =
    Right(Identified(Version(p.id, p.version), p.sourceData))
}
