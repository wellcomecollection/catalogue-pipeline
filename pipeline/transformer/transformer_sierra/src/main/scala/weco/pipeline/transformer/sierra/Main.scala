package weco.pipeline.transformer.sierra

import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.transfer.s3.S3TransferManager
import weco.catalogue.source_model.sierra.SierraTransformable
import weco.catalogue.source_model.Implicits._
import weco.pipeline.transformer.sierra.services.SierraSourceDataRetriever
import weco.pipeline.transformer.{Transformer, TransformerMain}
import weco.storage.store.s3.S3TypedStore
import weco.typesafe.WellcomeTypesafeApp

object Main extends WellcomeTypesafeApp {
  implicit val s3Client: S3Client = S3Client.builder().build()
  implicit val s3TransferManager: S3TransferManager = S3TransferManager.builder().build()

  val transformer: Transformer[SierraTransformable] =
    (id: String, transformable: SierraTransformable, version: Int) =>
      SierraTransformer(transformable, version).toEither

  val transformer = new TransformerMain(
    sourceName = "Sierra",
    transformer = transformer,
    sourceDataRetriever = new SierraSourceDataRetriever(S3TypedStore[SierraTransformable])
  )

  runWithConfig { config =>
    transformer.run(config)
  }
}
