package weco.pipeline.transformer.tei

import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.transfer.s3.S3TransferManager
import weco.catalogue.source_model.Implicits._
import weco.pipeline.transformer.TransformerMain
import weco.pipeline.transformer.tei.service.TeiSourceDataRetriever
import weco.storage.store.s3.S3TypedStore
import weco.typesafe.WellcomeTypesafeApp

object Main extends WellcomeTypesafeApp {
  def createTransformer(s3Client: S3Client,
                        s3TransferManager: S3TransferManager) = {
    implicit val client: S3Client = s3Client
    implicit val transferManager: S3TransferManager = s3TransferManager

    new TeiTransformer(teiReader = S3TypedStore[String])
  }

  val transformer = new TransformerMain(
    sourceName = "TEI",
    createTransformer = createTransformer,
    createSourceDataRetriever = (_, _) => new TeiSourceDataRetriever
  )

  runWithConfig { config =>
    transformer.run(config)
  }
}
