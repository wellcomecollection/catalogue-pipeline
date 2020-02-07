package uk.ac.wellcome.calm_adapter

import java.time.Instant

case class CalmRecord(
  id: String,
  data: Map[String, String],
  retrievedAt: Instant
)
