package weco.pipeline.merger.config

import com.typesafe.config.Config
import weco.elasticsearch.typesafe.ElasticBuilder.buildElasticClientConfig
import weco.elasticsearch.typesafe.ElasticConfig
import weco.lambda.DownstreamBuilder.buildDownstreamTarget
import weco.lambda.{ApplicationConfig, DownstreamTarget, LambdaConfigurable}

case class MergerConfig(
  upstreamElasticConfig: ElasticConfig,
  downstreamElasticConfig: ElasticConfig,
  identifiedWorkIndex: String,
  denormalisedWorkIndex: String,
  initialImageIndex: String,
  workDownstreamTarget: DownstreamTarget,
  pathDownstreamTarget: DownstreamTarget,
  pathConcatDownstreamTarget: DownstreamTarget,
  imageDownstreamTarget: DownstreamTarget
) extends ApplicationConfig

trait MergerConfigurable extends LambdaConfigurable[MergerConfig] {
  import weco.typesafe.config.builders.EnrichConfig._

  def build(rawConfig: Config): MergerConfig =
    MergerConfig(
      upstreamElasticConfig = buildElasticClientConfig(rawConfig, "upstream"),
      downstreamElasticConfig =
        buildElasticClientConfig(rawConfig, "downstream"),
      identifiedWorkIndex =
        rawConfig.requireString(("es.identified-works.index")),
      denormalisedWorkIndex =
        rawConfig.requireString("es.denormalised-works.index"),
      initialImageIndex = rawConfig.requireString("es.initial-images.index"),
      workDownstreamTarget = buildDownstreamTarget(rawConfig, "work-sender"),
      pathDownstreamTarget = buildDownstreamTarget(rawConfig, "path-sender"),
      pathConcatDownstreamTarget =
        buildDownstreamTarget(rawConfig, "path-concatenator-sender"),
      imageDownstreamTarget = buildDownstreamTarget(rawConfig, "image-sender")
    )
}
