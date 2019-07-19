package uk.ac.wellcome.models.parse

import scala.util.control.Exception
import java.time.LocalDate
import java.time.DateTimeException
import uk.ac.wellcome.models.work.internal.InstantRange

sealed trait TimePeriod

/**
  *  An exact or ambigous date
  */
sealed trait FuzzyDate extends TimePeriod
case class CalendarDate(day: Int, month: Int, year: Int) extends FuzzyDate
case class Year(year: Int) extends FuzzyDate
case class MonthAndYear(month: Int, year: Int) extends FuzzyDate
case class Month(month: Int) extends FuzzyDate
case class Day(day: Int) extends FuzzyDate

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

  implicit val convertCalendarDate =
    new ToInstantRange[CalendarDate] {
      def apply(value: CalendarDate): InstantRange =
        InstantRange(localDate(value), localDate(value))
    }

  implicit val convertYear =
    new ToInstantRange[Year] {
      def apply(value: Year): InstantRange =
        InstantRange(yearStart(value.year), yearEnd(value.year))
    }

  implicit val convertMonthAndYear =
    new ToInstantRange[MonthAndYear] {
      def apply(value: MonthAndYear): InstantRange =
        InstantRange(
          monthStart(value.month, value.year),
          monthEnd(value.month, value.year))
    }

  implicit val convertYearRange =
    new ToInstantRange[FuzzyDateRange[Year, Year]] {
      def apply(value: FuzzyDateRange[Year, Year]): InstantRange =
        InstantRange(yearStart(value.from.year), yearEnd(value.to.year))
    }

  implicit val convertCalendarDateRange =
    new ToInstantRange[FuzzyDateRange[CalendarDate, CalendarDate]] {
      def apply(
        value: FuzzyDateRange[CalendarDate, CalendarDate]): InstantRange =
        InstantRange(localDate(value.from), localDate(value.to))
    }

  implicit val convertMonthRangeAcrossYears =
    new ToInstantRange[FuzzyDateRange[MonthAndYear, MonthAndYear]] {
      def apply(
        value: FuzzyDateRange[MonthAndYear, MonthAndYear]): InstantRange =
        InstantRange(
          monthStart(value.from.month, value.from.year),
          monthEnd(value.to.month, value.to.year))
    }

  implicit val convertMonthRangeWithinAYear =
    new ToInstantRange[FuzzyDateRange[Month, MonthAndYear]] {
      def apply(value: FuzzyDateRange[Month, MonthAndYear]): InstantRange =
        InstantRange(
          monthStart(value.from.month, value.to.year),
          monthEnd(value.to.month, value.to.year))
    }

  implicit val convertDayRangeWithinAMonth =
    new ToInstantRange[FuzzyDateRange[Day, CalendarDate]] {
      def apply(value: FuzzyDateRange[Day, CalendarDate]): InstantRange =
        InstantRange(
          localDate(value.from.day, value.to.month, value.to.year),
          localDate(value.to))
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
}
