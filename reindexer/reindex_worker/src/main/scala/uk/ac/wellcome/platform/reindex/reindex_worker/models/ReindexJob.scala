package uk.ac.wellcome.platform.reindex.reindex_worker.models

import weco.messaging.sns.SNSConfig
import weco.storage.dynamo.DynamoConfig

case class ReindexJob(
  parameters: ReindexParameters,
  dynamoConfig: DynamoConfig,
  snsConfig: SNSConfig
)
