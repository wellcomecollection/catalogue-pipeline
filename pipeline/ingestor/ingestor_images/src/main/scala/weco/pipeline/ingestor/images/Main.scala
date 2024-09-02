package weco.pipeline.ingestor.images

import weco.catalogue.internal_model.Implicits._
import weco.json.JsonUtil._
import weco.pipeline.ingestor.common.IngestorMain
import weco.typesafe.WellcomeTypesafeApp

object Main extends WellcomeTypesafeApp {
  val ingestor = new IngestorMain(
    name = "images",
    inputIndexField = "es.augmented-images.index",
    outputIndexField = "es.indexed-images.index",
    transform = ImageTransformer.deriveData
  )

  runWithConfig {
    config =>
      ingestor.run(config)
  }
}
