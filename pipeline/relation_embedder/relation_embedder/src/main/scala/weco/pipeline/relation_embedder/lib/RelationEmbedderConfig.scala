package weco.pipeline.relation_embedder.lib

import com.typesafe.config.Config
import weco.lambda.ElasticBuilder.buildElasticClientConfig
import weco.lambda._
import weco.messaging.typesafe.SNSBuilder.buildSNSConfig

case class RelationEmbedderConfig(
  mergedWorkIndex: String,
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
      mergedWorkIndex = rawConfig.requireString("es.merged-works.index"),
      denormalisedWorkIndex =
        rawConfig.requireString("es.denormalised-works.index"),
      maxBatchWeight = rawConfig.requireInt("es.works.batch_size"),
      completeTreeScroll =
        rawConfig.requireInt("es.works.scroll.complete_tree"),
      affectedWorksScroll =
        rawConfig.requireInt("es.works.scroll.affected_works"),
      elasticConfig = buildElasticClientConfig(rawConfig),
      downstreamTarget = {
        rawConfig.requireString("relation_embedder.use_downstream") match {
          case "sns"   => SNS(buildSNSConfig(rawConfig))
          case "stdio" => StdOut
        }
      }
    )
}
