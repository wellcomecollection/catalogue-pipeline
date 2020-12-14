package uk.ac.wellcome.platform.reindex.reindex_worker.models.source

import uk.ac.wellcome.storage.s3.S3ObjectLocation

case class CalmSourceRecord(
  id: String,
  payload: S3ObjectLocation,
  version: Int
)
