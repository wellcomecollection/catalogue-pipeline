package weco.pipeline.transformer.miro

import software.amazon.awssdk.services.s3.S3Client
import weco.catalogue.source_model.Implicits._
import weco.pipeline.transformer.TransformerMain
import weco.pipeline.transformer.miro.Implicits._
import weco.pipeline.transformer.miro.services.MiroSourceDataRetriever
import weco.pipeline.transformer.miro.source.MiroRecord
import weco.storage.store.s3.S3TypedStore
import weco.storage.streaming.Codec._
import weco.typesafe.WellcomeTypesafeApp

object Main extends WellcomeTypesafeApp {
  implicit val s3Client: S3Client = S3Client.builder().build()

  val transformer = new TransformerMain(
    sourceName = "Miro",
    transformer = new MiroRecordTransformer,
    sourceDataRetriever = new MiroSourceDataRetriever(S3TypedStore[MiroRecord])
  )

  runWithConfig { config =>
    transformer.run(config)
  }
}
