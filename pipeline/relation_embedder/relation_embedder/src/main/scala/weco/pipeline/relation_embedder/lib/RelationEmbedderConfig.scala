package weco.pipeline.relation_embedder.lib

import com.typesafe.config.Config
import weco.elasticsearch.typesafe.ElasticBuilder.buildElasticClientConfig
import weco.lambda.DownstreamBuilder.buildDownstreamTarget
import weco.elasticsearch.typesafe.ElasticConfig
import weco.lambda._

case class RelationEmbedderConfig(
  denormalisedWorkIndex: String,
  maxBatchWeight: Int,
  completeTreeScroll: Int,
  affectedWorksScroll: Int,
  elasticConfig: ElasticConfig,
  downstreamTarget: DownstreamTarget
) extends ApplicationConfig

trait RelationEmbedderConfigurable
    extends LambdaConfigurable[RelationEmbedderConfig] {
  import weco.typesafe.config.builders.EnrichConfig._

  def build(rawConfig: Config): RelationEmbedderConfig =
    RelationEmbedderConfig(
      denormalisedWorkIndex =
        rawConfig.requireString("es.denormalised-works.index"),
      maxBatchWeight = rawConfig.requireInt("es.works.batch_size"),
      completeTreeScroll =
        rawConfig.requireInt("es.works.scroll.complete_tree"),
      affectedWorksScroll =
        rawConfig.requireInt("es.works.scroll.affected_works"),
      elasticConfig = buildElasticClientConfig(rawConfig),
      downstreamTarget = buildDownstreamTarget(rawConfig)
    )
}
