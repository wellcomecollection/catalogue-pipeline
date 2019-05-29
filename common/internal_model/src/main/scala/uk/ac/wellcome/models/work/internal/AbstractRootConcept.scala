package uk.ac.wellcome.models.work.internal

import java.time.{LocalDateTime, Year}
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder}
import java.time.temporal.ChronoField

import uk.ac.wellcome.models.work.text.TextNormalisation._

import scala.util.Try

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

case class DateRange(label: String,
                     from: LocalDateTime,
                     to: LocalDateTime,
                     inferred: Boolean)
    extends AbstractConcept
object DateRange {
  type GetDateRange = (String, LocalDateTime) => DateRange
  type DateRangeParser = (DateTimeFormatter, GetDateRange)

  val parsers: List[(String, GetDateRange)] = List(
    (
      "yyyy",
      (label: String, from: LocalDateTime) =>
        DateRange(
          label,
          from,
          to = from.plusYears(1).minusNanos(1),
          inferred = false)
    ),
    (
      "'['yyyy']'",
      (label: String, from: LocalDateTime) =>
        DateRange(
          label,
          from,
          to = from.plusYears(1).minusNanos(1),
          inferred = true)
    )
  )

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

  def parse(label: String): Option[DateRange] = {
    parsers.toStream.map {
      case (pattern, getDateRange) =>
        Try(LocalDateTime.parse(label, formatterWithDefaults(pattern)))
          .map(getDateRange(label, _))
    } find (_.isSuccess) map (_.get)
  }
}

case class Period(label: String) extends AbstractConcept
object Period {
  def normalised(label: String): Period = {
    Period(trimTrailing(label, '.'))
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
