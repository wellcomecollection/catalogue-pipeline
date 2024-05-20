package weco.pipeline.transformer.ebsco.service

import weco.catalogue.source_model.EbscoSourcePayload
import weco.catalogue.source_model.ebsco.{
  EbscoDeletedSourceData,
  EbscoSourceData,
  EbscoUpdatedSourceData
}
import weco.pipeline.transformer.SourceDataRetriever
import weco.storage.{Identified, NoVersionExistsError, ReadError, Version}

class EbscoSourceDataRetriever
    extends SourceDataRetriever[EbscoSourcePayload, EbscoSourceData] {

  override def lookupSourceData(
    payload: EbscoSourcePayload
  ): Either[ReadError, Identified[Version[String, Int], EbscoSourceData]] = {

    (payload.deleted, payload.location) match {
      case (true, _) =>
        Right(
          Identified(
            Version(payload.id, payload.version),
            EbscoDeletedSourceData(
              modifiedTime = payload.time
            )
          )
        )

      case (false, Some(location)) =>
        Right(
          Identified(
            Version(payload.id, payload.version),
            EbscoUpdatedSourceData(
              s3Location = location,
              modifiedTime = payload.time
            )
          )
        )

      case (false, None) =>
        Left(
          NoVersionExistsError(
            s"Missing location for EbscoSourcePayload ${payload.id}, version ${payload.version}"
          )
        )
    }
  }
}
