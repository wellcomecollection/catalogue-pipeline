package weco.catalogue.source_model.miro

import java.time.Instant

case class MiroUpdateEvent(
  description: String,
  message: String,
  date: Instant,
  user: String
)
