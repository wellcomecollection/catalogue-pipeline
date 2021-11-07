package weco.pipeline.ingestor.works

import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.index.WorksIndexConfig
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Indexed}
import weco.elasticsearch.IndexConfig
import weco.pipeline.ingestor.common.IngestorMain

object Main extends IngestorMain[Work[Denormalised], Work[Indexed]] {
  override val name: String = "works"

  override val inputIndexField: String = "es.denormalised-works.index"
  override val outputIndexField: String = "es.indexed-works.index"

  override val indexConfig: IndexConfig = WorksIndexConfig.indexed

  override val transform = WorkTransformer.deriveData

  runIngestor()
}
