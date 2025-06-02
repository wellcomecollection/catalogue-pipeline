package weco.pipeline.matcher.config

import com.typesafe.config.Config
import weco.elasticsearch.typesafe.ElasticBuilder.buildElasticClientConfig
import weco.elasticsearch.typesafe.ElasticConfig
import weco.lambda.DownstreamBuilder.buildDownstreamTarget
import weco.lambda.{ApplicationConfig, DownstreamTarget, LambdaConfigurable}
import weco.storage.dynamo.DynamoConfig
import weco.storage.typesafe.DynamoBuilder
import weco.storage.locking.dynamo.DynamoLockDaoConfig
import weco.typesafe.config.builders.EnrichConfig.RichConfig

case class MatcherConfig(
  elasticConfig: ElasticConfig,
  index: String,
  dynamoConfig: DynamoConfig,
  dynamoLockDAOConfig: DynamoLockDaoConfig,
  downstreamConfig: DownstreamTarget
) extends ApplicationConfig

trait MatcherConfigurable extends LambdaConfigurable[MatcherConfig] {

  def build(rawConfig: Config): MatcherConfig =
    MatcherConfig(
      elasticConfig = buildElasticClientConfig(rawConfig),
      index = rawConfig.requireString("es.index"),
      dynamoConfig = DynamoBuilder.buildDynamoConfig(rawConfig),
      dynamoLockDAOConfig = DynamoLockDaoConfigBuilder(rawConfig),
      downstreamConfig = buildDownstreamTarget(rawConfig)
    )
}
