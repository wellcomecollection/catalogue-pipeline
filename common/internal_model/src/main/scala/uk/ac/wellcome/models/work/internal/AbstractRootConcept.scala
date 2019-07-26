package uk.ac.wellcome.models.work.internal

import java.time.{Instant, LocalDate, LocalDateTime, ZoneOffset}
import uk.ac.wellcome.models.work.text.TextNormalisation._
import uk.ac.wellcome.models.parse.Parser
import uk.ac.wellcome.models.parse.parsers.DateParser

sealed trait AbstractRootConcept {
  val label: String
}

sealed trait AbstractConcept extends AbstractRootConcept

case class Concept(label: String) extends AbstractConcept
object Concept {
  def normalised(label: String): Concept = {
    Concept(trimTrailing(label, '.'))
  }
}

// We're not extending this yet, as we don't actually want it to be part of
// the Display model as yet before we've started testing, but in future it
// might extend AbstractConcept
case class InstantRange(from: Instant,
                        to: Instant,
                        label: String = "",
                        inferred: Boolean = false) {

  def withInferred(inferred: Boolean): InstantRange =
    InstantRange(from, to, label, inferred)

  // TODO: is label necessary? appears to always be same as Period.label
  def withLabel(label: String): InstantRange =
    InstantRange(from, to, label, inferred)
}

object InstantRange {

  def apply(from: LocalDate, to: LocalDate, label: String): InstantRange =
    InstantRange(
      from.atStartOfDay(),
      to.atStartOfDay().plusDays(1).minusNanos(1),
      label
    )

  def apply(
    from: LocalDateTime, to: LocalDateTime, label: String): InstantRange =
    InstantRange(
      from.toInstant(ZoneOffset.UTC),
      to.toInstant(ZoneOffset.UTC),
      label
    )

  def parse(label: String)(
    implicit parser: Parser[InstantRange]): Option[InstantRange] =
    parser(label)
}

case class Period(label: String, range: Option[InstantRange])
    extends AbstractConcept
object Period {
  def apply(label: String): Period = {
    val normalisedLabel = trimTrailing(label, '.')
    Period(normalisedLabel, InstantRange.parse(normalisedLabel))
  }
}

case class Place(label: String) extends AbstractConcept
object Place {
  def normalised(label: String): Place = {
    Place(trimTrailing(label, ':'))
  }
}
sealed trait AbstractAgent extends AbstractRootConcept

case class Agent(
  label: String
) extends AbstractAgent
object Agent {
  def normalised(label: String): Agent = {
    Agent(trimTrailing(label, ','))
  }
}

case class Organisation(
  label: String
) extends AbstractAgent
object Organisation {
  def normalised(label: String): Organisation = {
    Organisation(trimTrailing(label, ','))
  }
}

case class Person(label: String,
                  prefix: Option[String] = None,
                  numeration: Option[String] = None)
    extends AbstractAgent
object Person {
  def normalised(label: String,
                 prefix: Option[String] = None,
                 numeration: Option[String] = None): Person = {
    Person(
      label = trimTrailing(label, ','),
      prefix = prefix,
      numeration = numeration)
  }
}
