package weco.pipeline.transformer.calm

import software.amazon.awssdk.services.s3.S3Client
import weco.catalogue.source_model.calm.CalmRecord
import weco.catalogue.source_model.Implicits._
import weco.pipeline.transformer.TransformerMain
import weco.pipeline.transformer.calm.services.CalmSourceDataRetriever
import weco.storage.store.s3.S3TypedStore
import weco.typesafe.WellcomeTypesafeApp

object Main extends WellcomeTypesafeApp {
  implicit val s3Client: S3Client = S3Client.builder().build()

  val transformer = new TransformerMain(
    sourceName = "CALM",
    transformer = CalmTransformer,
    sourceDataRetriever =
      new CalmSourceDataRetriever(recordReadable = S3TypedStore[CalmRecord])
  )

  runWithConfig {
    config =>
      transformer.run(config)
  }
}
