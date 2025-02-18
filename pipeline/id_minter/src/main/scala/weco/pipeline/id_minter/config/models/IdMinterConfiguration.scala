package weco.pipeline.id_minter.config.models

import com.typesafe.config.Config
import weco.elasticsearch.typesafe.ElasticBuilder.buildElasticClientConfig
import weco.elasticsearch.typesafe.ElasticConfig
import weco.lambda.DownstreamBuilder.buildDownstreamTarget
import weco.lambda.{ApplicationConfig, DownstreamTarget, LambdaConfigurable}
import weco.pipeline.id_minter.config.builders.IdentifiersTableBuilder
import weco.typesafe.config.builders.EnrichConfig._

case class IdMinterConfig(
  rdsClientConfig: RDSClientConfig,
  identifiersTableConfig: IdentifiersTableConfig,
  upstreamElasticConfig: ElasticConfig,
  downstreamElasticConfig: ElasticConfig,
  sourceIndex: String,
  targetIndex: String,
  downstreamConfig: DownstreamTarget
) extends ApplicationConfig

trait IdMinterConfigurable extends LambdaConfigurable[IdMinterConfig] {
  def build(rawConfig: Config): IdMinterConfig = {
    IdMinterConfig(
      rdsClientConfig = RDSClientConfig(rawConfig),
      identifiersTableConfig = IdentifiersTableBuilder.buildConfig(rawConfig),
      upstreamElasticConfig = buildElasticClientConfig(rawConfig, "upstream"),
      downstreamElasticConfig =
        buildElasticClientConfig(rawConfig, "downstream"),
      sourceIndex = rawConfig.requireString("es.upstream.index"),
      targetIndex = rawConfig.requireString("es.downstream.index"),
      downstreamConfig = buildDownstreamTarget(rawConfig)
    )
  }
}
