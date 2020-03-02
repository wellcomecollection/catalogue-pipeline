package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.Index

case class ElasticConfig(
  index: Index
)

object ElasticConfig {
  // We use this to share config across API applications
  // i.e. The API and the snapshot generator.
  def apply(): ElasticConfig = ElasticConfig(index = Index("v2-20200228"))
}
