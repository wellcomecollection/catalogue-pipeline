package uk.ac.wellcome.pipeline_storage.typesafe

import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import io.circe.Decoder
import uk.ac.wellcome.elasticsearch.typesafe.ElasticBuilder
import uk.ac.wellcome.pipeline_storage.ElasticRetriever
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

object ElasticRetrieverBuilder {
  def buildRetriever[T](config: Config, namespace: String = "")(implicit decoder: Decoder[T]): ElasticRetriever[T] =
    new ElasticRetriever[T](
      client = ElasticBuilder.buildElasticClient(config),
      index = Index(config.requireString(s"es.$namespace.index"))
    )
}
