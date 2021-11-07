package weco.pipeline.transformer.calm

import com.amazonaws.services.s3.AmazonS3
import weco.catalogue.source_model.CalmSourcePayload
import weco.catalogue.source_model.calm.CalmRecord
import weco.json.JsonUtil._
import weco.pipeline.transformer.TransformerMain
import weco.pipeline.transformer.calm.models.CalmSourceData
import weco.pipeline.transformer.calm.services.CalmSourceDataRetriever
import weco.storage.store.s3.S3TypedStore

object Main extends TransformerMain[CalmSourcePayload, CalmSourceData] {
  override val sourceName = "CALM"

  override def createTransformer(
    implicit s3Client: AmazonS3): CalmTransformer.type =
    CalmTransformer

  override def createSourceDataRetriever(implicit s3Client: AmazonS3) =
    new CalmSourceDataRetriever(recordReadable = S3TypedStore[CalmRecord])

  runTransformer()
}
