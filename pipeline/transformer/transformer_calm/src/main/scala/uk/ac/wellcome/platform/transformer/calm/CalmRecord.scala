package uk.ac.wellcome.platform.transformer.calm

import java.time.Instant

case class CalmRecord(
  id: String,
  data: Map[String, List[String]],
  retrievedAt: Instant,
  published: Boolean = false
)
