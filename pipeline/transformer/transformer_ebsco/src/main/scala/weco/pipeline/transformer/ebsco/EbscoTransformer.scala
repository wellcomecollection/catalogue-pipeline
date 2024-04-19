package weco.pipeline.transformer.ebsco

import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.source_model.ebsco.EbscoSourceData
import weco.pipeline.transformer.Transformer
import weco.pipeline.transformer.result.Result
import weco.storage.providers.s3.S3ObjectLocation
import weco.storage.store.Readable

class EbscoTransformer(store: Readable[S3ObjectLocation, String])
    extends Transformer[EbscoSourceData] {
  override def apply(
    id: String,
    sourceData: EbscoSourceData,
    version: Int
  ): Result[Work[WorkState.Source]] =
    Left(new Throwable("Not implemented"))
}
