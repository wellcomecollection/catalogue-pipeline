package weco.pipeline.transformer.miro

import com.amazonaws.services.s3.AmazonS3
import weco.catalogue.source_model.MiroSourcePayload
import weco.catalogue.source_model.miro.MiroSourceOverrides
import weco.json.JsonUtil._
import weco.pipeline.transformer.TransformerMain
import weco.pipeline.transformer.miro.Implicits._
import weco.pipeline.transformer.miro.models.MiroMetadata
import weco.pipeline.transformer.miro.services.MiroSourceDataRetriever
import weco.pipeline.transformer.miro.source.MiroRecord
import weco.storage.store.s3.S3TypedStore
import weco.storage.streaming.Codec._

object Main extends TransformerMain[MiroSourcePayload, (MiroRecord, MiroSourceOverrides, MiroMetadata)] {
  override val sourceName: String = "Miro"

  override def createTransformer(implicit s3Client: AmazonS3) =
    new MiroRecordTransformer

  override def createSourceDataRetriever(implicit s3Client: AmazonS3) =
    new MiroSourceDataRetriever(miroReadable = S3TypedStore[MiroRecord])

  runWithConfig { config =>
    runTransformer(config)
  }
}
