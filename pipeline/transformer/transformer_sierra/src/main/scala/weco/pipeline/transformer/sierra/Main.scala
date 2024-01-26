package weco.pipeline.transformer.sierra

import software.amazon.awssdk.services.s3.S3Client
import weco.catalogue.source_model.sierra.SierraTransformable
import weco.catalogue.source_model.Implicits._
import weco.pipeline.transformer.sierra.services.SierraSourceDataRetriever
import weco.pipeline.transformer.TransformerMain
import weco.storage.store.s3.S3TypedStore
import weco.typesafe.WellcomeTypesafeApp

object Main extends WellcomeTypesafeApp {
  implicit val s3Client: S3Client = S3Client.builder().build()

  val transformer = new TransformerMain(
    sourceName = "Sierra",
    transformer = (id: String, transformable: SierraTransformable, version: Int) =>
      SierraTransformer(transformable, version).toEither,
    sourceDataRetriever = new SierraSourceDataRetriever(
      S3TypedStore[SierraTransformable]
    )
  )

  runWithConfig {
    config =>
      transformer.run(config)
  }
}
