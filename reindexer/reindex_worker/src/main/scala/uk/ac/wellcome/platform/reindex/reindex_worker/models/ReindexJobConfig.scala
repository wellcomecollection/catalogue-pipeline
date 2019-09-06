package uk.ac.wellcome.platform.reindex.reindex_worker.models

import uk.ac.wellcome.storage.dynamo.DynamoConfig

case class ReindexJobConfig[Destination](
  dynamoConfig: DynamoConfig,
  destinationConfig: Destination
)
