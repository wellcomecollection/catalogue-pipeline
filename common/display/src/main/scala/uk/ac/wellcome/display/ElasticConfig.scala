package uk.ac.wellcome.display

import buildinfo.BuildInfo
import com.sksamuel.elastic4s.Index

// This is here as display is the only module shared
// between api and snapshot_generator.
case class ElasticConfig(
  worksIndex: Index,
  imagesIndex: Index
)

object ElasticConfig {

  def apply(): ElasticConfig =
    ElasticConfig(
      worksIndex = Index(s"works-${BuildInfo.version}"),
      imagesIndex = Index(s"images-${BuildInfo.version}")
    )
}
