package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.Index

case class ElasticConfig(
  worksIndex: Index,
  imagesIndex: Index
)

object ElasticConfig {
  // We use this to share config across API applications
  // i.e. The API and the snapshot generator.
  val indexDate = "20201023"

  def apply(): ElasticConfig =
    ElasticConfig(
      worksIndex = Index(s"works-$indexDate"),
      imagesIndex = Index(s"images-$indexDate")
    )
}
