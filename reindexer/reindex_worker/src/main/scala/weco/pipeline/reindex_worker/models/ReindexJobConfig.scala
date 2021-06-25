package weco.pipeline.reindex_worker.models

import weco.storage.dynamo.DynamoConfig

case class ReindexJobConfig[Destination](
  dynamoConfig: DynamoConfig,
  destinationConfig: Destination,
  source: ReindexSource
)
