package weco.pipeline.transformer.ebsco

import software.amazon.awssdk.services.s3.S3Client
import weco.catalogue.source_model.Implicits._
import weco.pipeline.transformer.TransformerMain
import weco.pipeline.transformer.ebsco.service.EbscoSourceDataRetriever
import weco.storage.store.s3.S3TypedStore
import weco.typesafe.WellcomeTypesafeApp

object Main extends WellcomeTypesafeApp {
  implicit val s3Client: S3Client = S3Client.builder().build()

  val transformer = new TransformerMain(
    sourceName = "EBSCO",
    transformer = new EbscoTransformer(S3TypedStore[String]),
    sourceDataRetriever = new EbscoSourceDataRetriever
  )

  runWithConfig {
    config =>
      transformer.run(config)
  }
}
