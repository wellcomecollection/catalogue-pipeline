package uk.ac.wellcome.models.parse

import scala.util.control.Exception
import java.time.{LocalDate, DateTimeException}
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
    convert[MonthAndYear](value =>
      InstantRange(
        monthStart(value.month, value.year),
        monthEnd(value.month, value.year),
        value.label))

  implicit val convertCentury =
    convert[Century](value =>
      convertYearRange(centuryToFuzzyDateRange(value.century)))

  implicit val convertCenturyAndDecade =
    convert[CenturyAndDecade](value =>
      convertYearRange(centuryAndDecadeToFuzzyDateRange(value.century,
                                                        value.decade)))

  implicit val convertYearRange =
    convert[FuzzyDateRange[Year, Year]](value =>
      InstantRange(yearStart(value.from.year),
                   yearEnd(value.to.year),
                   value.label))

  implicit val convertCalendarDateRange =
    convert[FuzzyDateRange[CalendarDate, CalendarDate]](value =>
      InstantRange(localDate(value.from), localDate(value.to), value.label))

  implicit val convertMonthRangeAcrossYears =
    convert[FuzzyDateRange[MonthAndYear, MonthAndYear]](value =>
      InstantRange(
        monthStart(value.from.month, value.from.year),
        monthEnd(value.to.month, value.to.year),
        value.label))

  implicit val convertMonthRangeWithinAYear =
    convert[FuzzyDateRange[Month, MonthAndYear]](value =>
      InstantRange(
        monthStart(value.from.month, value.to.year),
        monthEnd(value.to.month, value.to.year),
        value.label))

  implicit val convertDayRangeWithinAMonth =
    convert[FuzzyDateRange[Day, CalendarDate]](value =>
      InstantRange(
        localDate(value.from.day, value.to.month, value.to.year),
        localDate(value.to),
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
    century: Int, decade: Int): FuzzyDateRange[Year, Year] =
    FuzzyDateRange(Year(century * 100 + decade * 10),
                   Year(century * 100 + decade * 10 + 9))
}
