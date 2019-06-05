package uk.ac.wellcome.models.work.generators

import java.time.{LocalDateTime, ZoneOffset}

import uk.ac.wellcome.models.work.internal._

trait InstantRangeGenerators {
  def createInstantRangeWith(label: String,
                             from: String,
                             to: String,
                             inferred: Boolean) =
    InstantRange(
      label,
      from = LocalDateTime.parse(from).toInstant(ZoneOffset.UTC),
      to = LocalDateTime.parse(to).toInstant(ZoneOffset.UTC),
      inferred = inferred
    )
}
