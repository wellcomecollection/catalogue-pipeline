package uk.ac.wellcome.platform.matcher.locking

import java.time.Duration

import uk.ac.wellcome.storage.dynamo.DynamoConfig

case class DynamoRowLockDaoConfig(
  dynamoConfig: DynamoConfig,
  duration: Duration = Duration.ofSeconds(180)
)
