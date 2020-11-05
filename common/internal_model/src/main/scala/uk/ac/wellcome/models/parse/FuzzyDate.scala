package uk.ac.wellcome.models.parse

import scala.util.control.Exception
import java.time.{DateTimeException, LocalDate}
import uk.ac.wellcome.models.work.internal.InstantRange

/**
  *  An exact or ambigous date
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

/**
  *  A continuous period over some days / months / years
  *
  *  @param from The start date
  *  @param to The end date
  */
case class FuzzyDateRange[F <: FuzzyDate, T <: FuzzyDate](from: F, to: T)
    extends TimePeriod

object FuzzyDateRange {
  def combine[
    FF <: FuzzyDate,
    FT <: FuzzyDate,
    TF <: FuzzyDate,
    TT <: FuzzyDate
  ](from: FuzzyDateRange[FF, FT],
    to: FuzzyDateRange[TF, TT]): FuzzyDateRange[FF, TT] =
    FuzzyDateRange(from.from, to.to)
}

/**
  *  Type class for conversion of TimePeriod types to InstantRange
  */
trait ToInstantRange[T <: TimePeriod] {

  def apply(value: T): InstantRange

  def safeConvert(value: T): Option[InstantRange] =
    Exception.catching(classOf[DateTimeException]) opt apply(value)
}

object ToInstantRange extends DateHelpers {

  def convert[T <: TimePeriod](f: T => InstantRange) = new ToInstantRange[T] {
    def apply(value: T): InstantRange = f(value)
  }

  implicit val convertCalendarDate =
    convert[CalendarDate](value =>
      InstantRange(localDate(value), localDate(value), value.label))

  implicit val convertYear =
    convert[Year](value =>
      InstantRange(yearStart(value.year), yearEnd(value.year), value.label))

  implicit val convertMonthAndYear =
    convert[MonthAndYear](
      value =>
        InstantRange(
          monthStart(value.month, value.year),
          monthEnd(value.month, value.year),
          value.label))

  implicit val convertCentury =
    convert[Century](value =>
      convertYearRange(centuryToFuzzyDateRange(value.century)))

  implicit val convertCenturyRange =
    convert[FuzzyDateRange[Century, Century]](
      value =>
        convertYearRange(
          FuzzyDateRange.combine(
            centuryToFuzzyDateRange(value.from.century),
            centuryToFuzzyDateRange(value.to.century)
          )
      )
    )

  implicit val convertCenturyAndDecade =
    convert[CenturyAndDecade](
      value =>
        convertYearRange(
          centuryAndDecadeToFuzzyDateRange(value.century, value.decade)))

  implicit val convertDecadeRange =
    convert[FuzzyDateRange[CenturyAndDecade, CenturyAndDecade]](
      value =>
        convertYearRange(
          FuzzyDateRange.combine(
            centuryAndDecadeToFuzzyDateRange(
              value.from.century,
              value.from.decade),
            centuryAndDecadeToFuzzyDateRange(value.to.century, value.to.decade)
          )
      )
    )

  implicit val convertYearCentury =
    convert[FuzzyDateRange[Year, Century]](
      value =>
        convertYearRange(
          FuzzyDateRange(
            value.from,
            centuryToFuzzyDateRange(value.to.century).to
          )
      )
    )

  implicit val convertCenturyYear =
    convert[FuzzyDateRange[Century, Year]](
      value =>
        convertYearRange(
          FuzzyDateRange(
            centuryToFuzzyDateRange(value.from.century).from,
            value.to
          )
      )
    )

  implicit val convertYearRange =
    convert[FuzzyDateRange[Year, Year]](
      value =>
        InstantRange(
          yearStart(value.from.year),
          yearEnd(value.to.year),
          value.label))

  implicit val convertCalendarDateRange =
    convert[FuzzyDateRange[CalendarDate, CalendarDate]](value =>
      InstantRange(localDate(value.from), localDate(value.to), value.label))

  implicit val convertCalendarMonth =
    convert[FuzzyDateRange[CalendarDate, MonthAndYear]](
      value =>
        InstantRange(
          localDate(value.from),
          monthEnd(value.to.month, value.to.year),
          value.label))

  implicit val convertMonthCalendar =
    convert[FuzzyDateRange[MonthAndYear, CalendarDate]](
      value =>
        InstantRange(
          monthStart(value.from.month, value.from.year),
          localDate(value.to),
          value.label
      ))

  implicit val convertMonthToDateWithinYear =
    convert[FuzzyDateRange[MonthAndYear, MonthAndDay]](
      value =>
        InstantRange(
          monthStart(value.from.month, value.from.year),
          localDate(value.to.day, value.to.month, value.from.year),
          value.label))

  implicit val convertCalendarDateToMonthDateWithinYear =
    convert[FuzzyDateRange[CalendarDate, MonthAndDay]](
      value =>
        InstantRange(
          localDate(value.from),
          localDate(value.to.day, value.to.month, value.from.year),
          value.label))

  implicit val convertMonthDateToCalendarDateWithinYear =
    convert[FuzzyDateRange[MonthAndDay, CalendarDate]](
      value =>
        InstantRange(
          localDate(value.from.day, value.from.month, value.to.year),
          localDate(value.to),
          value.label))

  implicit val convertMonthRangeAcrossYears =
    convert[FuzzyDateRange[MonthAndYear, MonthAndYear]](
      value =>
        InstantRange(
          monthStart(value.from.month, value.from.year),
          monthEnd(value.to.month, value.to.year),
          value.label))

  implicit val convertYearToMonth =
    convert[FuzzyDateRange[Year, MonthAndYear]](
      value =>
        InstantRange(
          yearStart(value.from.year),
          monthEnd(value.to.month, value.to.year),
          value.label))

  implicit val convertYearToDate =
    convert[FuzzyDateRange[Year, CalendarDate]](
      value =>
        InstantRange(
          yearStart(value.from.year),
          localDate(value.to),
          value.label))

  implicit val convertDateToYear =
    convert[FuzzyDateRange[CalendarDate, Year]](value =>
      InstantRange(localDate(value.from), yearEnd(value.to.year), value.label))

  implicit val convertMonthRangeWithinAYear =
    convert[FuzzyDateRange[Month, MonthAndYear]](
      value =>
        InstantRange(
          monthStart(value.from.month, value.to.year),
          monthEnd(value.to.month, value.to.year),
          value.label))

  implicit val convertDayToMonthWithinYear =
    convert[FuzzyDateRange[CalendarDate, Month]](
      value =>
        InstantRange(
          localDate(value.from),
          monthEnd(value.to.month, value.from.year),
          value.label
      )
    )

  implicit val convertMonthRangeWithinYearReversed =
    convert[FuzzyDateRange[MonthAndYear, Month]](
      value =>
        InstantRange(
          monthStart(value.from.month, value.from.year),
          monthEnd(value.to.month, value.from.year),
          value.label
      ))

  implicit val convertDayRangeWithinAMonth =
    convert[FuzzyDateRange[Day, CalendarDate]](
      value =>
        InstantRange(
          localDate(value.from.day, value.to.month, value.to.year),
          localDate(value.to),
          value.label))

  implicit val convertDayRangeWithinAMonthReversed =
    convert[FuzzyDateRange[CalendarDate, Day]](
      value =>
        InstantRange(
          localDate(value.from),
          localDate(value.to.day, value.from.month, value.from.year),
          value.label))
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
      case FuzzyDateRange(from, to) =>
        f"${from.label}-${to.label}"
    }
}

/**
  *  Mixin containing helper functions for generating LocalDate objects
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
    century: Int): FuzzyDateRange[Year, Year] =
    FuzzyDateRange(Year(century * 100), Year(century * 100 + 99))

  protected def centuryAndDecadeToFuzzyDateRange(
    century: Int,
    decade: Int): FuzzyDateRange[Year, Year] =
    FuzzyDateRange(
      Year(century * 100 + decade * 10),
      Year(century * 100 + decade * 10 + 9))
}
