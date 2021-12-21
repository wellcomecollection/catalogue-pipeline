package weco.pipeline.transformer.tei

import com.amazonaws.services.s3.AmazonS3
import weco.catalogue.source_model.Implicits._
import weco.pipeline.transformer.TransformerMain
import weco.pipeline.transformer.tei.service.TeiSourceDataRetriever
import weco.storage.store.s3.S3TypedStore
import weco.typesafe.WellcomeTypesafeApp

object Main extends WellcomeTypesafeApp {
  def createTransformer(s3Client: AmazonS3) = {
    implicit val s: AmazonS3 = s3Client
    new TeiTransformer(teiReader = S3TypedStore[String])
  }

  val transformer = new TransformerMain(
    sourceName = "TEI",
    createTransformer = createTransformer,
    createSourceDataRetriever = _ => new TeiSourceDataRetriever
  )

  runWithConfig { config =>
    transformer.run(config)
  }
}
