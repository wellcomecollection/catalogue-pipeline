package weco.pipeline.transformer.tei

import software.amazon.awssdk.services.s3.S3Client
import weco.catalogue.source_model.Implicits._
import weco.pipeline.transformer.TransformerMain
import weco.pipeline.transformer.tei.service.TeiSourceDataRetriever
import weco.storage.store.s3.S3TypedStore
import weco.typesafe.WellcomeTypesafeApp

object Main extends WellcomeTypesafeApp {
  implicit val s3Client: S3Client = S3Client.builder().build()

  val transformer = new TransformerMain(
    sourceName = "TEI",
    transformer = new TeiTransformer(teiReader = S3TypedStore[String]),
    sourceDataRetriever = new TeiSourceDataRetriever
  )

  runWithConfig {
    config =>
      transformer.run(config)
  }
}
