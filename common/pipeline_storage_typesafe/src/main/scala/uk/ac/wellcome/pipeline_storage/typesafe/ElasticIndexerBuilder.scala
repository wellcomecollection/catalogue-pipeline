package uk.ac.wellcome.pipeline_storage.typesafe

import com.sksamuel.elastic4s.{ElasticClient, Index}
import com.typesafe.config.Config
import io.circe.{Decoder, Encoder}
import uk.ac.wellcome.elasticsearch.IndexConfig
import uk.ac.wellcome.pipeline_storage.{ElasticIndexer, Indexable}
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

import scala.concurrent.ExecutionContext

object ElasticIndexerBuilder {
  def apply[T: Indexable](
    config: Config,
    client: ElasticClient,
    namespace: String = "",
    indexConfig: IndexConfig,
    skipReindexingIdenticalDocuments: Boolean = false
  )(
    implicit
    ec: ExecutionContext,
    decoder: Decoder[T],
    encoder: Encoder[T]
  ): ElasticIndexer[T] =
    new ElasticIndexer[T](
      client = client,
      index = Index(config.requireString(s"es.$namespace.index")),
      config = indexConfig,
      skipReindexingIdenticalDocuments = skipReindexingIdenticalDocuments
    )
}
