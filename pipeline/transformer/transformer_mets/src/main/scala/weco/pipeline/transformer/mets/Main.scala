package weco.pipeline.transformer.mets

import com.amazonaws.services.s3.AmazonS3
import weco.json.JsonUtil._
import weco.pipeline.transformer.TransformerMain
import weco.pipeline.transformer.mets.services.MetsSourceDataRetriever
import weco.pipeline.transformer.mets.transformer.MetsXmlTransformer
import weco.storage.store.s3.S3TypedStore
import weco.typesafe.WellcomeTypesafeApp

object Main extends WellcomeTypesafeApp {
  def createTransformer(s3Client: AmazonS3) = {
    implicit val s: AmazonS3 = s3Client
    new MetsXmlTransformer(S3TypedStore[String])
  }

  val transformer = new TransformerMain(
    sourceName = "METS",
    createTransformer = createTransformer,
    createSourceDataRetriever = _ => new MetsSourceDataRetriever
  )

  runWithConfig { config =>
    transformer.run(config)
  }
}
