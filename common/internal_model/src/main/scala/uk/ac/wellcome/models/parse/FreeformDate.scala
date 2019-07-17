package uk.ac.wellcome.models.parse

import java.time.LocalDate

import uk.ac.wellcome.models.work.internal.InstantRange

/**
 *  Models freeform text representing some date or period
 *
 *  @param date The date or date range
 *  @param inferred Whether the date is known exactly or inferred
 */
case class FreeformDate(date: Either[FuzzyDate, FuzzyDateRange],
                        inferred: Boolean = false) {

  def instantRange : Option[InstantRange] =
    (date match {
      case Left(date) => date.instantRange
      case Right(dateRange) => dateRange.instantRange
    }) map { _ withInferred inferred }

  def withInferred(inferred : Boolean) : FreeformDate =
    FreeformDate(date, inferred)
}

/**
 *  A exact or ambigous date
 */
sealed trait FuzzyDate extends DateHelpers {

  def instantRange : Option[InstantRange] =
    this match {

      case ExactDate(date) =>
        Some(InstantRange(date))

      case Year(year) =>
        Some(InstantRange(yearStart(year), yearEnd(year)))

      case MonthAndYear(month, year) =>
        Some(InstantRange(monthStart(month, year), monthEnd(month, year)))

      case _ => None
    }
}

case class ExactDate(date: LocalDate) extends FuzzyDate
case class Year(year: Int) extends FuzzyDate
case class MonthAndYear(month: Int, year: Int) extends FuzzyDate
case class Month(day: Int) extends FuzzyDate
case class Day(day: Int) extends FuzzyDate

/**
 *  A continuos period over some days / months / years
 *
 *  @param from The start date
 *  @param to The end date
 */
case class FuzzyDateRange(from: FuzzyDate, to: FuzzyDate) extends DateHelpers {

  def instantRange : Option[InstantRange] =
    (from, to) match {

      case (ExactDate(fromDate), ExactDate(toDate)) =>
        Some(InstantRange(fromDate, toDate))

      case (Year(fromYear), Year(toYear)) =>
        Some(InstantRange(yearStart(fromYear), yearEnd(toYear)))

      case (MonthAndYear(fromMonth, fromYear),
            MonthAndYear(toMonth, toYear)) =>
        Some(InstantRange(monthStart(fromMonth, fromYear),
                          monthEnd(toMonth, toYear)))

      case (Month(fromMonth),
            MonthAndYear(toMonth, year)) =>
        Some(InstantRange(monthStart(fromMonth, year),
                          monthEnd(toMonth, year)))

      case (Day(day), ExactDate(date)) =>
        Some(InstantRange(LocalDate of (date.getYear, date.getMonth, day),
                          date))

      case _ => None
    }
}

/**
 *  Mixin containing helper functions for generating LocalDate objects
 */
trait DateHelpers {

  protected def monthStart(month: Int, year: Int) : LocalDate =
    LocalDate.of(year, month, 1)

  protected def monthEnd(month: Int, year: Int) : LocalDate =
    LocalDate.of(year, month, 1).plusMonths(1).minusDays(1)

  protected def yearStart(year: Int) : LocalDate =
      LocalDate.of(year, 1, 1)

  protected def yearEnd(year: Int) : LocalDate =
      LocalDate.of(year, 12, 31)
}
