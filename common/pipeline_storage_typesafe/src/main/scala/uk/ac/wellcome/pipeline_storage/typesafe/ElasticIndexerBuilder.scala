package uk.ac.wellcome.pipeline_storage.typesafe

import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import io.circe.Encoder
import uk.ac.wellcome.elasticsearch.IndexConfig
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.pipeline_storage.{ElasticIndexer, Indexable}
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

object ElasticIndexerBuilder {
  def apply[T: Indexable](
    config: Config,
    namespace: String = "",
    indexConfig: IndexConfig
  )(
    implicit
    ec: ExecutionContext,
    encoder: Encoder[T]
  ): ElasticIndexer[T] =
    new ElasticIndexer[T](
      client = ElasticBuilder.buildElasticClient(config),
      index = Index(config.requireString(s"es.$namespace.index")),
      config = indexConfig
    )
}
