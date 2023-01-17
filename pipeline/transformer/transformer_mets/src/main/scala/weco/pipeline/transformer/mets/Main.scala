package weco.pipeline.transformer.mets

import software.amazon.awssdk.services.s3.S3Client
import weco.catalogue.source_model.Implicits._
import weco.pipeline.transformer.TransformerMain
import weco.pipeline.transformer.mets.services.MetsSourceDataRetriever
import weco.pipeline.transformer.mets.transformer.MetsXmlTransformer
import weco.storage.store.s3.S3TypedStore
import weco.typesafe.WellcomeTypesafeApp

object Main extends WellcomeTypesafeApp {
  implicit val s3Client: S3Client = S3Client.builder().build()

  val transformer = new TransformerMain(
    sourceName = "METS",
    transformer = new MetsXmlTransformer(S3TypedStore[String]),
    sourceDataRetriever = new MetsSourceDataRetriever()
  )

  runWithConfig { config =>
    transformer.run(config)
  }
}
