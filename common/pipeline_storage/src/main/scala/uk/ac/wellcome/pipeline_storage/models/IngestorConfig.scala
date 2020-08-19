package uk.ac.wellcome.pipeline_storage.models

import scala.concurrent.duration.FiniteDuration

case class IngestorConfig(
  batchSize: Int,
  flushInterval: FiniteDuration
)
