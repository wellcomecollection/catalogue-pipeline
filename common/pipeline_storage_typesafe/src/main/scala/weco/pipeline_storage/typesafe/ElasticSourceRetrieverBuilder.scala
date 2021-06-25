package weco.pipeline_storage.typesafe

import com.sksamuel.elastic4s.{ElasticClient, Index}
import com.typesafe.config.Config
import io.circe.Decoder
import weco.typesafe.config.builders.EnrichConfig._
import weco.pipeline_storage.elastic.ElasticSourceRetriever

import scala.concurrent.ExecutionContext

object ElasticSourceRetrieverBuilder {
  def apply[T](
    config: Config,
    client: ElasticClient,
    namespace: String = ""
  )(
    implicit
    ec: ExecutionContext,
    decoder: Decoder[T]
  ): ElasticSourceRetriever[T] =
    new ElasticSourceRetriever[T](
      client = client,
      index = Index(config.requireString(s"es.$namespace.index"))
    )
}
