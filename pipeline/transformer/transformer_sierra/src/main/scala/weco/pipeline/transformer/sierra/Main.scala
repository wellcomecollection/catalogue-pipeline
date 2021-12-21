package weco.pipeline.transformer.sierra

import com.amazonaws.services.s3.AmazonS3
import weco.catalogue.source_model.sierra.SierraTransformable
import weco.catalogue.source_model.Implicits._
import weco.pipeline.transformer.sierra.services.SierraSourceDataRetriever
import weco.pipeline.transformer.{Transformer, TransformerMain}
import weco.storage.store.s3.S3TypedStore
import weco.typesafe.WellcomeTypesafeApp

object Main extends WellcomeTypesafeApp {
  def createTransformer(s3Client: AmazonS3): Transformer[SierraTransformable] =
    (id: String, transformable: SierraTransformable, version: Int) =>
      SierraTransformer(transformable, version).toEither

  def createSourceDataRetriever(s3Client: AmazonS3) = {
    implicit val s: AmazonS3 = s3Client
    new SierraSourceDataRetriever(
      sierraReadable = S3TypedStore[SierraTransformable]
    )
  }

  val transformer = new TransformerMain(
    sourceName = "Sierra",
    createTransformer = createTransformer,
    createSourceDataRetriever = createSourceDataRetriever
  )

  runWithConfig { config =>
    transformer.run(config)
  }
}
