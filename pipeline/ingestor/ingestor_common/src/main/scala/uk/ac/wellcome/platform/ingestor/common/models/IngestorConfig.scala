package uk.ac.wellcome.platform.ingestor.common.models

import scala.concurrent.duration.FiniteDuration

case class IngestorConfig(
  batchSize: Int,
  flushInterval: FiniteDuration
)
