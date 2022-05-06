package weco.pipeline.ingestor.works

import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.index.WorksIndexConfig
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.pipeline.ingestor.common.IngestorMain
import weco.pipeline.ingestor.works.models.IndexedWork
import weco.typesafe.WellcomeTypesafeApp
import weco.json.JsonUtil._

object Main extends WellcomeTypesafeApp {
  val ingestor =
    new IngestorMain[Work[WorkState.Denormalised], IndexedWork](
      name = "works",
      inputIndexField = "es.denormalised-works.index",
      outputIndexField = "es.indexed-works.index",
      indexConfig = WorksIndexConfig.indexed,
      transform = WorkTransformer.deriveData
    )

  runWithConfig { config =>
    ingestor.run(config)
  }
}
