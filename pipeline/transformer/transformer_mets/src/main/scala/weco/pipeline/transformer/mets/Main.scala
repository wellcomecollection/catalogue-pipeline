package weco.pipeline.transformer.mets

import com.amazonaws.services.s3.AmazonS3
import weco.catalogue.source_model.MetsSourcePayload
import weco.catalogue.source_model.mets.MetsSourceData
import weco.json.JsonUtil._
import weco.pipeline.transformer.TransformerMain
import weco.pipeline.transformer.mets.services.MetsSourceDataRetriever
import weco.pipeline.transformer.mets.transformer.MetsXmlTransformer
import weco.storage.store.s3.S3TypedStore

object Main extends TransformerMain[MetsSourcePayload, MetsSourceData] {
  override val sourceName: String = "METS"

  override def createTransformer(implicit s3Client: AmazonS3) =
    new MetsXmlTransformer(S3TypedStore[String])

  override def createSourceDataRetriever(implicit s3Client: AmazonS3) =
    new MetsSourceDataRetriever

  runWithConfig { config =>
    runTransformer(config)
  }
}
