package uk.ac.wellcome.pipeline_storage.typesafe

import com.sksamuel.elastic4s.{ElasticClient, Index}
import com.typesafe.config.Config
import io.circe.Encoder
import uk.ac.wellcome.elasticsearch.IndexConfig
import uk.ac.wellcome.pipeline_storage.{ElasticIndexer, Indexable}
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

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

  def buildWithIndexSuffix[T: Indexable](config: Config,
                           client: ElasticClient,
                           indexConfig: IndexConfig,
                           indexSuffix : String,
                             namespace: String = "")(
                            implicit
                            ec: ExecutionContext,
                            encoder: Encoder[T]
                          ): ElasticIndexer[T] =
    new ElasticIndexer[T](
      client = client,
      index = Index(s"${config.requireString(s"es.$namespace.index")}-$indexSuffix"),
      config = indexConfig
    )
}
