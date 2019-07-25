package uk.ac.wellcome.models.parse

import uk.ac.wellcome.models.work.internal.InstantRange
import fastparse._, NoWhitespace._
import DateParserImplicits._

/**
  *  Attempts to parse freeform text into a date or date range
  */
object FreeformDateParser extends Parser[InstantRange] with DateParserUtils {

  def parser[_: P] =
    (Start ~ inferredTimePeriod | timePeriod ~ End) map (_ withLabel "")

  def inferredTimePeriod[_: P] =
    ("[".? ~ timePeriod ~ "]") map (_ withInferred true)

  def timePeriod[_: P] =
    dateRange |
      year.toInstantRange |
      calendarDate.toInstantRange |
      monthAndYear.toInstantRange

  def dateRange[_: P] =
    (year to year).toInstantRange |
      (calendarDate to calendarDate).toInstantRange |
      (monthAndYear to monthAndYear).toInstantRange |
      (month to monthAndYear).toInstantRange |
      (day to calendarDate).toInstantRange

  def calendarDate[_: P] = numericDate | dayMonthYear | monthDayYear

  def numericDate[_: P] =
    (dayDigits ~ "/" ~ monthDigits ~ "/" ~ yearDigits)
      .map { case (d, m, y) => CalendarDate(d, m, y) }

  def dayMonthYear[_: P] =
    (writtenDay ~ ws ~ writtenMonth ~ ws ~ yearDigits)
      .map { case (d, m, y) => CalendarDate(d, m, y) }

  def monthDayYear[_: P] =
    (writtenMonth ~ ws ~ writtenDay ~ ws ~ yearDigits)
      .map { case (m, d, y) => CalendarDate(d, m, y) }

  def monthAndYear[_: P] =
    (monthFollowedByYear | yearFollowedByMonth)
      .map { case (m, y) => MonthAndYear(m, y) }

  def month[_: P] = writtenMonth map (Month(_))

  def day[_: P] = writtenDay map (Day(_))
}
