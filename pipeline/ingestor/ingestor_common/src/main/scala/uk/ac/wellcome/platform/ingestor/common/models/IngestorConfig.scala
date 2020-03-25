package uk.ac.wellcome.platform.ingestor.common.models

import com.sksamuel.elastic4s.Index

import scala.concurrent.duration.FiniteDuration

case class IngestorConfig(
  batchSize: Int,
  flushInterval: FiniteDuration,
  index: Index
)
