package uk.ac.wellcome.models.work.generators

import java.time.LocalDateTime

import uk.ac.wellcome.models.work.internal._

trait InstantRangeGenerators {

  def createInstantRangeWith(from: String,
                             to: String,
                             inferred: Boolean = false,
                             label: String = "") =
    InstantRange(LocalDateTime.parse(from),
                 LocalDateTime.parse(to))
      .withLabel(label)
      .withInferred(inferred)
}
