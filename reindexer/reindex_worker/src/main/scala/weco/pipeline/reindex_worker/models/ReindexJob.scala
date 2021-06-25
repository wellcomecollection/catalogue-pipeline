package weco.pipeline.reindex_worker.models

import weco.messaging.sns.SNSConfig
import weco.storage.dynamo.DynamoConfig

case class ReindexJob(
  parameters: ReindexParameters,
  dynamoConfig: DynamoConfig,
  snsConfig: SNSConfig
)
