package uk.ac.wellcome.platform.reindex.reindex_worker.models

import uk.ac.wellcome.messaging.sns.SNSConfig
import weco.storage.dynamo.DynamoConfig

case class ReindexJob(
  parameters: ReindexParameters,
  dynamoConfig: DynamoConfig,
  snsConfig: SNSConfig
)
