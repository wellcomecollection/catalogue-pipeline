package uk.ac.wellcome.platform.reindex.reindex_worker.models

import uk.ac.wellcome.storage.dynamo.DynamoConfig

case class ReindexJobConfig[DestinationConfig](
  dynamoConfig: DynamoConfig,
  destination: DestinationConfig
)
