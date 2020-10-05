package uk.ac.wellcome.platform.snapshot_generator.models

case class CompletedSnapshotJob(
  snapshotJob: SnapshotJob,
  targetLocation: String
)
