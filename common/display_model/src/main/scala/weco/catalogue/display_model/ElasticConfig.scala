package weco.catalogue.display_model

import com.sksamuel.elastic4s.Index

// This is here as display is the only module shared
// between api and snapshot_generator.
case class ElasticConfig(
  worksIndex: Index,
  imagesIndex: Index
)

trait ElasticConfigBase {
  // We use this to share config across API applications
  // i.e. The API and the snapshot generator.
  val pipelineDate = "2022-04-04"
}

object PipelineClusterElasticConfig extends ElasticConfigBase {
  def apply(): ElasticConfig =
    ElasticConfig(
      worksIndex = Index(s"works-indexed-$pipelineDate"),
      imagesIndex = Index(s"images-indexed-$pipelineDate")
    )
}
