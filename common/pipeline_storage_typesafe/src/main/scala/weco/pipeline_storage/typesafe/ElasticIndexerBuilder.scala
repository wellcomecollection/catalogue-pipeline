package weco.pipeline_storage.typesafe

import com.sksamuel.elastic4s.{ElasticClient, Index}
import com.typesafe.config.Config
import io.circe.Encoder
import weco.elasticsearch.IndexConfig
import weco.pipeline_storage.Indexable
import weco.pipeline_storage.elastic.ElasticIndexer
import weco.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

object ElasticIndexerBuilder {
  def apply[T: Indexable](
    config: Config,
    client: ElasticClient,
    namespace: String = "",
    indexConfig: IndexConfig
  )(
    implicit
    ec: ExecutionContext,
    encoder: Encoder[T]
  ): ElasticIndexer[T] =
    new ElasticIndexer[T](
      client = client,
      index = Index(config.requireString(s"es.$namespace.index")),
      config = indexConfig
    )
}
