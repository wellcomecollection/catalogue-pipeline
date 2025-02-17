package weco.pipeline.id_minter.config.models

import com.typesafe.config.Config
import weco.elasticsearch.typesafe.ElasticBuilder.buildElasticClientConfig
import weco.elasticsearch.typesafe.ElasticConfig
import weco.lambda.DownstreamBuilder.buildDownstreamTarget
import weco.lambda.{ApplicationConfig, DownstreamTarget, LambdaConfigurable}
import weco.messaging.sns.SNSConfig
import weco.messaging.typesafe.SNSBuilder
import weco.pipeline.id_minter.config.builders.IdentifiersTableBuilder
import weco.typesafe.config.builders.EnrichConfig._

case class IdMinterConfig(
  rdsClientConfig: RDSClientConfig,
  identifiersTableConfig: IdentifiersTableConfig,
  elasticConfig: ElasticConfig,
  snsConfig: SNSConfig,
  sourceIndex: String,
  targetIndex: String,
  downstreamConfig: DownstreamTarget
) extends ApplicationConfig

trait IdMinterConfigurable extends LambdaConfigurable[IdMinterConfig] {
  def build(rawConfig: Config): IdMinterConfig =
    IdMinterConfig(
      rdsClientConfig = RDSClientConfig(rawConfig),
      identifiersTableConfig = IdentifiersTableBuilder.buildConfig(rawConfig),
      elasticConfig = buildElasticClientConfig(rawConfig),
      snsConfig = SNSBuilder.buildSNSConfig(rawConfig),
      sourceIndex = rawConfig.requireString("es.source-works.index"),
      targetIndex = rawConfig.requireString("es.identified-works.index"),
      downstreamConfig = buildDownstreamTarget(rawConfig)
    )
}
