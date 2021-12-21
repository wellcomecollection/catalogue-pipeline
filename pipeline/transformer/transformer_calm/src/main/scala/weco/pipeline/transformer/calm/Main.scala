package weco.pipeline.transformer.calm

import com.amazonaws.services.s3.AmazonS3
import weco.catalogue.source_model.calm.CalmRecord
import weco.catalogue.source_model.Implicits._
import weco.pipeline.transformer.TransformerMain
import weco.pipeline.transformer.calm.services.CalmSourceDataRetriever
import weco.storage.store.s3.S3TypedStore
import weco.typesafe.WellcomeTypesafeApp

object Main extends WellcomeTypesafeApp {
  def createSourceDataTransformer(
    s3Client: AmazonS3): CalmSourceDataRetriever = {
    implicit val s: AmazonS3 = s3Client
    new CalmSourceDataRetriever(recordReadable = S3TypedStore[CalmRecord])
  }

  val transformer = new TransformerMain(
    sourceName = "CALM",
    createTransformer = _ => CalmTransformer,
    createSourceDataRetriever = createSourceDataTransformer
  )

  runWithConfig { config =>
    transformer.run(config)
  }
}
