package uk.ac.wellcome.models.work.internal

import java.time.{Instant, LocalDate, LocalDateTime, ZoneOffset}

import uk.ac.wellcome.models.parse.Parser
import uk.ac.wellcome.models.work.internal.InstantRange.instantOrdering

// We're not extending this yet, as we don't actually want it to be part of
// the Display model as yet before we've started testing, but in future it
// might extend AbstractConcept
case class InstantRange(from: Instant,
                        to: Instant,
                        label: String = "",
                        inferred: Boolean = false) {

  def withInferred(inferred: Boolean): InstantRange =
    InstantRange(from, to, label, inferred)

  def withLabel(label: String): InstantRange =
    InstantRange(from, to, label, inferred)

  def +(x: InstantRange): InstantRange = InstantRange(
    from = instantOrdering.min(x.from, from),
    to = instantOrdering.max(x.to, to),
    label = s"$label, ${x.label}",
    inferred = inferred || x.inferred
  )
}

object InstantRange {
  val instantOrdering: Ordering[Instant] = _ compareTo _

  def apply(from: LocalDate, to: LocalDate, label: String): InstantRange =
    InstantRange(
      from.atStartOfDay(),
      to.atStartOfDay().plusDays(1).minusNanos(1),
      label
    )

  def apply(from: LocalDateTime,
            to: LocalDateTime,
            label: String): InstantRange =
    InstantRange(
      from.toInstant(ZoneOffset.UTC),
      to.toInstant(ZoneOffset.UTC),
      label
    )

  def parse(label: String)(
    implicit parser: Parser[InstantRange]): Option[InstantRange] =
    parser(label)
}
