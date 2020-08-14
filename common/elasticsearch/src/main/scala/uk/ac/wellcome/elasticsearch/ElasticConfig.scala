package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.Index

case class ElasticConfig(
  worksIndex: Index,
  imagesIndex: Index
)

object ElasticConfig {
  // We use this to share config across API applications
  // i.e. The API and the snapshot generator.
  def apply(): ElasticConfig =
    ElasticConfig(
      worksIndex = Index("works-20200813"),
      imagesIndex = Index("images-20200813")
    )
}
