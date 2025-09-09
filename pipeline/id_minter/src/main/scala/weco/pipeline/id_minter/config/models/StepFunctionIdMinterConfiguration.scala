package weco.pipeline.id_minter.config.models

import com.typesafe.config.Config
import weco.elasticsearch.typesafe.ElasticBuilder.buildElasticClientConfig
import weco.elasticsearch.typesafe.ElasticConfig
import weco.lambda.{ApplicationConfig, LambdaConfigurable}
import weco.pipeline.id_minter.config.builders.IdentifiersTableBuilder
import weco.typesafe.config.builders.EnrichConfig._

case class StepFunctionIdMinterConfig(
  rdsClientConfig: RDSClientConfig,
  identifiersTableConfig: IdentifiersTableConfig,
  upstreamElasticConfig: ElasticConfig,
  downstreamElasticConfig: ElasticConfig,
  sourceIndex: String,
  targetIndex: String
) extends ApplicationConfig

trait StepFunctionIdMinterConfigurable extends LambdaConfigurable[StepFunctionIdMinterConfig] {
  def build(rawConfig: Config): StepFunctionIdMinterConfig = {
    StepFunctionIdMinterConfig(
      rdsClientConfig = RDSClientConfig(rawConfig),
      identifiersTableConfig = IdentifiersTableBuilder.buildConfig(rawConfig),
      upstreamElasticConfig = buildElasticClientConfig(rawConfig, "upstream"),
      downstreamElasticConfig =
        buildElasticClientConfig(rawConfig, "downstream"),
      sourceIndex = rawConfig.requireString("es.upstream.index"),
      targetIndex = rawConfig.requireString("es.downstream.index")
    )
  }
}