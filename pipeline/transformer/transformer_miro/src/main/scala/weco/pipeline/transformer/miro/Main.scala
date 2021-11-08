package weco.pipeline.transformer.miro

import com.amazonaws.services.s3.AmazonS3
import weco.json.JsonUtil._
import weco.pipeline.transformer.TransformerMain
import weco.pipeline.transformer.miro.Implicits._
import weco.pipeline.transformer.miro.services.MiroSourceDataRetriever
import weco.pipeline.transformer.miro.source.MiroRecord
import weco.storage.store.s3.S3TypedStore
import weco.storage.streaming.Codec._
import weco.typesafe.WellcomeTypesafeApp

object Main extends WellcomeTypesafeApp {
  def createSourceDataRetriever(s3Client: AmazonS3) = {
    implicit val s: AmazonS3 = s3Client
    new MiroSourceDataRetriever(miroReadable = S3TypedStore[MiroRecord])
  }

  val transformer = new TransformerMain(
    sourceName = "Miro",
    createTransformer = _ => new MiroRecordTransformer,
    createSourceDataRetriever = createSourceDataRetriever
  )

  runWithConfig { config =>
    transformer.run(config)
  }
}
