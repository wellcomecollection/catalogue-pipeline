package weco.pipeline.matcher.config

import com.typesafe.config.Config
import weco.storage.locking.dynamo.DynamoLockDaoConfig
import weco.storage.typesafe.DynamoBuilder
import weco.typesafe.config.builders.EnrichConfig.RichConfig

import scala.concurrent.duration.DurationInt

object DynamoLockDaoConfigBuilder {
  def apply(rawConfig: Config) = new DynamoLockDaoConfig(
    dynamoConfig =
      DynamoBuilder.buildDynamoConfig(rawConfig, namespace = "locking"),
    expiryTime = rawConfig
      .getDurationOption(s"aws.locking.dynamo.lockExpiryTime")
      .getOrElse(3.minutes)
  )
}
