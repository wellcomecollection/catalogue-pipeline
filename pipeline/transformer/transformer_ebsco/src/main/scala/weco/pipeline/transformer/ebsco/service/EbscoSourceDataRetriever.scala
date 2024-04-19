package weco.pipeline.transformer.ebsco.service


import weco.catalogue.source_model.EbscoSourcePayload
import weco.catalogue.source_model.ebsco.EbscoSourceData
import weco.pipeline.transformer.SourceDataRetriever
import weco.storage.{Identified, NoVersionExistsError, ReadError, Version}

class EbscoSourceDataRetriever
  extends SourceDataRetriever[EbscoSourcePayload, EbscoSourceData] {

  override def lookupSourceData(
                                 payload: EbscoSourcePayload
                               ): Either[ReadError, Identified[Version[String, Int], EbscoSourceData]] =
    Left(NoVersionExistsError(""))
}
