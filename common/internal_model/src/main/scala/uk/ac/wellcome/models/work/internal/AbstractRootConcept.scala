package uk.ac.wellcome.models.work.internal

import java.time.{Instant, LocalDateTime, Year, ZoneOffset}
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoField

import uk.ac.wellcome.models.work.text.TextNormalisation._

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

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

case class InstantRange(label: String,
                        from: Instant,
                        to: Instant,
                        inferred: Boolean)
// We're not extending this yet, as we don't
// actually want it to be part of the Display
// model as yet before we've started testing.
// extends AbstractConcept
object InstantRange {
  // We use this apply as it's easier to work with date math on LocalDateTime than it is on Instant
  def apply(label: String,
            from: LocalDateTime,
            to: LocalDateTime,
            inferred: Boolean): InstantRange =
    InstantRange(
      label = label,
      from = from.toInstant(ZoneOffset.UTC),
      to = to.toInstant(ZoneOffset.UTC),
      inferred = inferred)

  private type ParseDateTimeToInstantRange =
    (String, LocalDateTime) => InstantRange
  private type DatePattern = String

  private val parsers: List[(DatePattern, ParseDateTimeToInstantRange)] = List(
    (
      "yyyy",
      (label: String, from: LocalDateTime) =>
        InstantRange(
          label,
          from,
          to = from.plusYears(1).minusNanos(1),
          inferred = false)
    ),
    (
      "'['yyyy']'",
      (label: String, from: LocalDateTime) =>
        InstantRange(
          label,
          from = from,
          to = from.plusYears(1).minusNanos(1),
          inferred = true)
    )
  )

  // This explicitly defaults missing pieces to incomplete dates such as "1909" to
  // then return 1909-01-01 to allow us to format it to the complete ISO8601 standard
  private def formatterWithDefaults(pattern: String): DateTimeFormatter =
    new DateTimeFormatterBuilder()
      .appendPattern(pattern)
      .parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
      .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
      .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
      .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
      .parseDefaulting(ChronoField.DAY_OF_MONTH, 1)
      .parseDefaulting(ChronoField.MONTH_OF_YEAR, 1)
      .parseDefaulting(ChronoField.YEAR_OF_ERA, Year.now().getValue())
      .toFormatter()

  @tailrec
  private def findParser(
    label: String,
    parsers: List[(DatePattern, ParseDateTimeToInstantRange)])
    : Option[InstantRange] = {

    parsers match {
      case (pattern: DatePattern, getInstantRange: ParseDateTimeToInstantRange) :: tail =>
        val tryLocalDateTime = Try(
          LocalDateTime.parse(label, formatterWithDefaults(pattern)))

        tryLocalDateTime match {
          case Success(localDateTime) =>
            Some(getInstantRange(label, localDateTime))
          case Failure(_) => findParser(label, tail)
        }

      case _ => None
    }
  }

  def parse(label: String): Option[InstantRange] = findParser(label, parsers)
}

case class Period(label: String, range: Option[InstantRange])
    extends AbstractConcept
object Period {
  def apply(label: String): Period = {
    val normalisedLabel = trimTrailing(label, '.')
    val range = InstantRange.parse(normalisedLabel)
    Period(normalisedLabel, range)
  }

  def normalised(label: String): Period = {
    val normalisedLabel = trimTrailing(label, '.')
    Period(normalisedLabel)
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
