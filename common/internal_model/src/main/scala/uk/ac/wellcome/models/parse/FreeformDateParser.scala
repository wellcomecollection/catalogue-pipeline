package uk.ac.wellcome.models.parse

import uk.ac.wellcome.models.work.internal.InstantRange
import fastparse._, NoWhitespace._
import ToInstantRange._
import DateParserImplicits._

/**
  *  Attempts to parse freeform text into a date or date range
  */
object FreeformDateParser extends Parser[InstantRange] with DateParserUtils {

  def parser[_: P] =
    Start ~ (inferredTimePeriod | timePeriod) ~ End

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

  def year[_: P] = yearDigits map (Year(_))

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

trait DateParserUtils extends ParserUtils {

  def monthFollowedByYear[_: P] =
    (writtenMonth ~ ws ~ yearDigits)

  def yearFollowedByMonth[_: P] =
    (yearDigits ~ ws ~ writtenMonth) map { case (y, m) => (m, y) }

  def writtenDay[_: P] = dayDigits ~ ordinalIndicator.?

  def writtenMonth[_: P] =
    StringInIgnoreCase(
      "january",
      "febuary",
      "march",
      "april",
      "may",
      "june",
      "july",
      "august",
      "september",
      "october",
      "november",
      "december",
      "jan",
      "feb",
      "mar",
      "apr",
      "may",
      "jun",
      "jul",
      "aug",
      "sep",
      "oct",
      "nov",
      "dec"
    ).!.map { name =>
      monthMapping.get(name.toLowerCase.substring(0, 3)).get
    }

  val monthMapping = Map(
    "jan" -> 1,
    "feb" -> 2,
    "mar" -> 3,
    "apr" -> 4,
    "may" -> 5,
    "jun" -> 6,
    "jul" -> 7,
    "aug" -> 8,
    "sep" -> 9,
    "oct" -> 10,
    "nov" -> 11,
    "dec" -> 12,
  )

  def dayDigits[_: P] =
    digit
      .rep(min = 1, max = 2)
      .!
      .map(_.toInt)
      .filter(value => value >= 1 && value <= 31)

  def monthDigits[_: P] =
    digit
      .rep(min = 1, max = 2)
      .!
      .map(_.toInt)
      .filter(value => value >= 1 && value <= 12)

  def yearDigits[_: P] =
    digit
      .rep(exactly = 4)
      .!
      .map(_.toInt)

  def ordinalIndicator[_: P] = StringIn("st", "nd", "rd", "th")
}

trait ParserUtils {

  def digit[_: P] = CharPred(_.isDigit)

  def ws[_: P] = " ".rep
}

/**
  *  Implicit classes used for conversion of P[A] to P[B] for some A / B
  */
object DateParserImplicits extends ParserUtils {

  implicit class ToDateRangeParser[F <: FuzzyDate](from: => P[F]) {

    def to[_: P, T <: FuzzyDate](to: => P[T]): P[FuzzyDateRange[F, T]] =
      (from ~ ws.? ~ "-" ~ ws.? ~ to)
        .map { case (f, t) => FuzzyDateRange(f, t) }
  }

  implicit class ToInstantRangeParser[T <: TimePeriod](parser: P[T]) {

    def toInstantRange[_: P](
      implicit toInstantRange: ToInstantRange[T]): P[InstantRange] =
      parser
        .map(toInstantRange.safeConvert(_))
        .filter(_.nonEmpty)
        .map(_.get)
  }
}
