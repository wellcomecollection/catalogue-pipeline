package weco.catalogue.internal_model.work

import weco.catalogue.internal_model.parse.Parser
import weco.catalogue.internal_model.work.InstantRange.instantOrdering

import java.time.{Instant, LocalDate, LocalDateTime, ZoneOffset}

// We're not extending this yet, as we don't actually want it to be part of
// the Display model as yet before we've started testing, but in future it
// might extend AbstractConcept
case class InstantRange(from: Instant, to: Instant, label: String = "") {

  def withLabel(label: String): InstantRange =
    InstantRange(from, to, label)

  def +(x: InstantRange): InstantRange = InstantRange(
    from = instantOrdering.min(x.from, from),
    to = instantOrdering.max(x.to, to),
    label = s"$label, ${x.label}",
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

  // We use the year 9999 here because it's the same convention
  // used by Sierra and in MARC 008 codes
  private val positiveInfinity = LocalDate
    .of(9999, 12, 31)
    .atStartOfDay()
    .plusDays(1)
    .minusNanos(1)
    .toInstant(ZoneOffset.UTC)

  private val negativeInfinity = LocalDate
    .of(-9999, 1, 1)
    .atStartOfDay()
    .toInstant(ZoneOffset.UTC)

  def after(start: InstantRange): InstantRange = InstantRange(
    from = start.from,
    to = positiveInfinity,
    label = start.label
  )

  def before(end: InstantRange): InstantRange = InstantRange(
    from = negativeInfinity,
    to = end.to,
    label = end.label
  )

  def parse(label: String)(
    implicit parser: Parser[InstantRange]): Option[InstantRange] =
    parser(label)
}
