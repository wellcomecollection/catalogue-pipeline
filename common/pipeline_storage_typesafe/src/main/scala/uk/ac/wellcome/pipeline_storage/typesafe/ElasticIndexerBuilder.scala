package uk.ac.wellcome.pipeline_storage.typesafe

import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import io.circe.Encoder
import uk.ac.wellcome.elasticsearch.IndexConfig
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.pipeline_storage.ElasticIndexer
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

object ElasticIndexerBuilder {
  def buildIndexer[T](
    config: Config,
    namespace: String = "",
    indexConfig: IndexConfig)(implicit encoder: Encoder[T]): ElasticIndexer[T] =
    new ElasticIndexer[T](
      client = ElasticBuilder.buildElasticClient(config),
      index = Index(config.requireString(s"es.$namespace.index")),
      config = indexConfig
    )
}
