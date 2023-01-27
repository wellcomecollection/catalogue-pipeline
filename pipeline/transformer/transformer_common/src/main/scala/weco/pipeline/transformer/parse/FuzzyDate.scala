package weco.pipeline.transformer.parse

import java.time.LocalDate

/** An exact or ambigous date
  */
sealed trait FuzzyDate extends TimePeriod
case class CalendarDate(day: Int, month: Int, year: Int) extends FuzzyDate
case class Year(year: Int) extends FuzzyDate
case class MonthAndYear(month: Int, year: Int) extends FuzzyDate
case class Month(month: Int) extends FuzzyDate
case class Day(day: Int) extends FuzzyDate
case class MonthAndDay(month: Int, day: Int) extends FuzzyDate
case class Century(century: Int) extends FuzzyDate
case class CenturyAndDecade(century: Int, decade: Int) extends FuzzyDate

/** A continuous period over some days / months / years
  *
  * @param from
  *   The start date
  * @param to
  *   The end date
  */
case class FuzzyDateRange[F <: FuzzyDate, T <: FuzzyDate](from: F, to: T)
    extends TimePeriod

object FuzzyDateRange {
  def combine[
    F <: FuzzyDate,
    T <: FuzzyDate
  ](
    from: FuzzyDateRange[F, _ <: FuzzyDate],
    to: FuzzyDateRange[_ <: FuzzyDate, T]
  ): FuzzyDateRange[F, T] =
    FuzzyDateRange(from.from, to.to)
}

sealed trait TimePeriod extends DateHelpers {
  lazy val label: String =
    this match {
      case CalendarDate(day, month, year) =>
        f"$year%04d/$month%02d/$day%02d"
      case Year(year) =>
        f"$year%04d"
      case Month(month) =>
        f"$month%02d"
      case Day(day) =>
        f"$day%02d"
      case MonthAndYear(month, year) =>
        f"$year%04d/$month%04d"
      case MonthAndDay(month, day) =>
        f"$month%02d/$day%02d"
      case Century(century) =>
        centuryToFuzzyDateRange(century).label
      case CenturyAndDecade(century, decade) =>
        centuryAndDecadeToFuzzyDateRange(century, decade).label

      // In Sierra, the year 9999 is used to indicate an open-ended range.
      // We still want to extract this value for date-based filtering, but we
      // shouldn't show it in the display labels.
      case FuzzyDateRange(from, to) if to == Year(9999) =>
        s"${from.label}-"

      case FuzzyDateRange(from, to) =>
        f"${from.label}-${to.label}"
    }
}

/** Mixin containing helper functions for generating LocalDate objects
  */
trait DateHelpers {

  protected def localDate(calendarDate: CalendarDate): LocalDate =
    LocalDate.of(calendarDate.year, calendarDate.month, calendarDate.day)

  protected def localDate(day: Int, month: Int, year: Int): LocalDate =
    LocalDate.of(year, month, day)

  protected def monthStart(month: Int, year: Int): LocalDate =
    LocalDate.of(year, month, 1)

  protected def monthEnd(month: Int, year: Int): LocalDate =
    LocalDate.of(year, month, 1).plusMonths(1).minusDays(1)

  protected def yearStart(year: Int): LocalDate =
    LocalDate.of(year, 1, 1)

  protected def yearEnd(year: Int): LocalDate =
    LocalDate.of(year, 12, 31)

  protected def centuryToFuzzyDateRange(
    century: Int
  ): FuzzyDateRange[Year, Year] =
    FuzzyDateRange(Year(century * 100), Year(century * 100 + 99))

  protected def centuryAndDecadeToFuzzyDateRange(
    century: Int,
    decade: Int
  ): FuzzyDateRange[Year, Year] =
    FuzzyDateRange(
      Year(century * 100 + decade * 10),
      Year(century * 100 + decade * 10 + 9)
    )
}
