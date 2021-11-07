package weco.pipeline.transformer.tei

import com.amazonaws.services.s3.AmazonS3
import weco.catalogue.source_model.TeiSourcePayload
import weco.catalogue.source_model.tei.TeiMetadata
import weco.pipeline.transformer.TransformerMain
import weco.pipeline.transformer.tei.service.TeiSourceDataRetriever
import weco.storage.store.s3.S3TypedStore

object Main extends TransformerMain[TeiSourcePayload, TeiMetadata] {
  override val sourceName: String = "TEI"

  override def createTransformer(implicit s3Client: AmazonS3) =
    new TeiTransformer(teiReader = S3TypedStore[String])

  override def createSourceDataRetriever(implicit s3Client: AmazonS3) =
    new TeiSourceDataRetriever

  runWithConfig { config =>
    runTransformer(config)
  }
}
