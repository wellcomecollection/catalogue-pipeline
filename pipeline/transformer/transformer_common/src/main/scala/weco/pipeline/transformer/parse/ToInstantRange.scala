package weco.pipeline.transformer.parse

import weco.catalogue.internal_model.work.InstantRange

import java.time.DateTimeException
import scala.util.control.Exception

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

  implicit val calendarDateToInstantRange =
    convert[CalendarDate](value =>
      InstantRange(localDate(value), localDate(value), value.label))

  implicit val yearToInstantRange =
    convert[Year](value =>
      InstantRange(yearStart(value.year), yearEnd(value.year), value.label))

  implicit val monthAndYearToInstantRange =
    convert[MonthAndYear](
      value =>
        InstantRange(
          monthStart(value.month, value.year),
          monthEnd(value.month, value.year),
          value.label))

  implicit val centuryToInstantRange =
    convert[Century](value =>
      yearToYearToInstantRange(centuryToFuzzyDateRange(value.century)))

  implicit val centuryToCenturyToInstantRange =
    convert[FuzzyDateRange[Century, Century]](
      value =>
        yearToYearToInstantRange(
          FuzzyDateRange.combine(
            centuryToFuzzyDateRange(value.from.century),
            centuryToFuzzyDateRange(value.to.century)
          )
      )
    )

  implicit val centuryAndDecadeToInstantRange =
    convert[CenturyAndDecade](
      value =>
        yearToYearToInstantRange(
          centuryAndDecadeToFuzzyDateRange(value.century, value.decade)))

  implicit val centuryAndDecadeToCenturyAndDecadeToInstantRange =
    convert[FuzzyDateRange[CenturyAndDecade, CenturyAndDecade]](
      value =>
        yearToYearToInstantRange(
          FuzzyDateRange.combine(
            centuryAndDecadeToFuzzyDateRange(
              value.from.century,
              value.from.decade),
            centuryAndDecadeToFuzzyDateRange(value.to.century, value.to.decade)
          )
      )
    )

  implicit val centuryAndDecadeToYearToInstantRange =
    convert[FuzzyDateRange[CenturyAndDecade, Year]](
      value =>
        yearToYearToInstantRange(
          FuzzyDateRange(
            Year(value.from.century * 100 + value.from.decade * 10),
            value.to
          )))

  implicit val yearToCenturyAndDecadeToInstantRange =
    convert[FuzzyDateRange[Year, CenturyAndDecade]](
      value =>
        yearToYearToInstantRange(
          FuzzyDateRange(
            value.from,
            Year(value.to.century * 100 + value.to.decade * 10 + 9)
          )))

  implicit val yearToCenturyToInstantRange =
    convert[FuzzyDateRange[Year, Century]](
      value =>
        yearToYearToInstantRange(
          FuzzyDateRange(
            value.from,
            centuryToFuzzyDateRange(value.to.century).to
          )
      )
    )

  implicit val centuryToYearToInstantRange =
    convert[FuzzyDateRange[Century, Year]](
      value =>
        yearToYearToInstantRange(
          FuzzyDateRange(
            centuryToFuzzyDateRange(value.from.century).from,
            value.to
          )
      )
    )

  implicit val centuryToDecadeToInstantRange =
    convert[FuzzyDateRange[Century, CenturyAndDecade]](
      value =>
        yearToYearToInstantRange(
          FuzzyDateRange(
            centuryToFuzzyDateRange(value.from.century).from,
            centuryAndDecadeToFuzzyDateRange(value.to.century, value.to.decade).to
          )
      )
    )

  implicit val yearToYearToInstantRange =
    convert[FuzzyDateRange[Year, Year]](
      value =>
        InstantRange(
          yearStart(value.from.year),
          yearEnd(value.to.year),
          value.label))

  implicit val calendarDateToCalendarDateToInstantRange =
    convert[FuzzyDateRange[CalendarDate, CalendarDate]](value =>
      InstantRange(localDate(value.from), localDate(value.to), value.label))

  implicit val calendarDateToMonthAndYearToInstantRange =
    convert[FuzzyDateRange[CalendarDate, MonthAndYear]](
      value =>
        InstantRange(
          localDate(value.from),
          monthEnd(value.to.month, value.to.year),
          value.label))

  implicit val monthAndYearToCalendarDateToInstantRange =
    convert[FuzzyDateRange[MonthAndYear, CalendarDate]](
      value =>
        InstantRange(
          monthStart(value.from.month, value.from.year),
          localDate(value.to),
          value.label
      ))

  implicit val monthAndYearToMonthAndDayToInstantRange =
    convert[FuzzyDateRange[MonthAndYear, MonthAndDay]](
      value =>
        InstantRange(
          monthStart(value.from.month, value.from.year),
          localDate(value.to.day, value.to.month, value.from.year),
          value.label))

  implicit val calendarDateToMonthAndDayToInstantRange =
    convert[FuzzyDateRange[CalendarDate, MonthAndDay]](
      value =>
        InstantRange(
          localDate(value.from),
          localDate(value.to.day, value.to.month, value.from.year),
          value.label))

  implicit val monthAndDayToCalendarDateToInstantRange =
    convert[FuzzyDateRange[MonthAndDay, CalendarDate]](
      value =>
        InstantRange(
          localDate(value.from.day, value.from.month, value.to.year),
          localDate(value.to),
          value.label))

  implicit val monthAndYearToMonthAndYearToInstantRange =
    convert[FuzzyDateRange[MonthAndYear, MonthAndYear]](
      value =>
        InstantRange(
          monthStart(value.from.month, value.from.year),
          monthEnd(value.to.month, value.to.year),
          value.label))

  implicit val yearToMonthAndYearToInstantRange =
    convert[FuzzyDateRange[Year, MonthAndYear]](
      value =>
        InstantRange(
          yearStart(value.from.year),
          monthEnd(value.to.month, value.to.year),
          value.label))

  implicit val monthAndYearToYearToInstantRange =
    convert[FuzzyDateRange[MonthAndYear, Year]](
      value =>
        InstantRange(
          monthStart(value.from.month, value.from.year),
          yearEnd(value.to.year),
          value.label))

  implicit val yearToCalendarDateToInstantRange =
    convert[FuzzyDateRange[Year, CalendarDate]](
      value =>
        InstantRange(
          yearStart(value.from.year),
          localDate(value.to),
          value.label))

  implicit val calendarDateToYearToInstantRange =
    convert[FuzzyDateRange[CalendarDate, Year]](value =>
      InstantRange(localDate(value.from), yearEnd(value.to.year), value.label))

  implicit val monthToMonthAndYearToInstantRange =
    convert[FuzzyDateRange[Month, MonthAndYear]](
      value =>
        InstantRange(
          monthStart(value.from.month, value.to.year),
          monthEnd(value.to.month, value.to.year),
          value.label))

  implicit val calendarDateToMonthToInstantRange =
    convert[FuzzyDateRange[CalendarDate, Month]](
      value =>
        InstantRange(
          localDate(value.from),
          monthEnd(value.to.month, value.from.year),
          value.label
      )
    )

  implicit val monthAndYearToMonthToInstantRange =
    convert[FuzzyDateRange[MonthAndYear, Month]](
      value =>
        InstantRange(
          monthStart(value.from.month, value.from.year),
          monthEnd(value.to.month, value.from.year),
          value.label
      ))

  implicit val dayToCalendarDateToInstantRange =
    convert[FuzzyDateRange[Day, CalendarDate]](
      value =>
        InstantRange(
          localDate(value.from.day, value.to.month, value.to.year),
          localDate(value.to),
          value.label))

  implicit val calendarDateToDayToInstantRange =
    convert[FuzzyDateRange[CalendarDate, Day]](
      value =>
        InstantRange(
          localDate(value.from),
          localDate(value.to.day, value.from.month, value.from.year),
          value.label))
}
