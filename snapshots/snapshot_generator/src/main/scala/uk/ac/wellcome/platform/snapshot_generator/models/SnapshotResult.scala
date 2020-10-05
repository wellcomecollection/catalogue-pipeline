package uk.ac.wellcome.platform.snapshot_generator.models

import java.time.Instant

import uk.ac.wellcome.storage.s3.S3ObjectLocation

case class SnapshotResult(
  indexName: String,
  documentCount: Int,
  displayModel: String,
  startedAt: Instant,
  finishedAt: Instant,
  s3Etag: String,
  s3Size: Long,
  s3Location: S3ObjectLocation
)
