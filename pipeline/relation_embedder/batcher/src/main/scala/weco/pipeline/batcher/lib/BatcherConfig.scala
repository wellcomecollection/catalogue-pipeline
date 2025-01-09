package weco.pipeline.batcher.lib

import com.typesafe.config.Config
import weco.lambda.DownstreamBuilder.buildDownstreamTarget
import weco.lambda.{
  ApplicationConfig,
  DownstreamTarget,
  LambdaConfigurable,
  SNS,
  StdOut
}
import weco.messaging.typesafe.SNSBuilder.buildSNSConfig

case class BatcherConfig(
  maxBatchSize: Int,
  downstreamTarget: DownstreamTarget
) extends ApplicationConfig

trait BatcherConfigurable extends LambdaConfigurable[BatcherConfig] {
  import weco.typesafe.config.builders.EnrichConfig._

  def build(rawConfig: Config): BatcherConfig =
    BatcherConfig(
      maxBatchSize = rawConfig.requireInt("batcher.max_batch_size"),
      downstreamTarget = buildDownstreamTarget(rawConfig)
    )
}
