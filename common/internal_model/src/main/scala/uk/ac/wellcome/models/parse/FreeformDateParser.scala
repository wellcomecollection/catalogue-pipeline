package uk.ac.wellcome.models.parse

import uk.ac.wellcome.models.work.internal.InstantRange
import fastparse._, NoWhitespace._
import ToInstantRange._

/**
 *  Attempts to parse freeform text into a date or date range
 */
object FreeformDateParser extends Parser[InstantRange] {

  def parser[_ : P] =
    Start ~ (inferredDate | date) ~ End

  def inferredDate[_ : P] =
    ("[".? ~ date ~ "]") map (_ withInferred true)

  def date[_ : P] =
    dateRange |
    toInstantRange(year) |
    toInstantRange(exactDate) |
    toInstantRange(monthAndYear)

  def dateRange[_ : P] =
    toInstantRange(monthRangeWithinAYear) |
    toInstantRange(monthRangeAcrossYears) |
    toInstantRange(yearRange) |
    toInstantRange(exactRange) |
    toInstantRange(dayRangeWithinAMonth)

  def yearRange[_ : P] =
    (year ~ range ~ year)
      .map { case (y1, y2) => FuzzyDateRange(y1, y2) }

  def exactRange[_ : P] =
    (exactDate ~ range ~ exactDate)
      .map { case (d1, d2) => FuzzyDateRange(d1, d2) }

  def monthRangeAcrossYears[_ : P] =
    (monthAndYear ~ range ~ monthAndYear)
      .map { case (my1, my2) => FuzzyDateRange(my1, my2) }

  def monthRangeWithinAYear[_ : P] =
    (month ~ range ~ monthAndYear)
      .map { case (m, my) => FuzzyDateRange(m, my) }

  def  dayRangeWithinAMonth[_ : P] =
    (day ~ range ~ exactDate)
      .map { case (a, b) => FuzzyDateRange(a, b) }

  def year[_ : P] =
    yearDigits.map(Year(_))

  def exactDate[_ : P] =
    numericDate | dayMonthYear | monthDayYear

  def numericDate[_ : P] =
    (dayDigits ~ "/" ~ monthDigits ~ "/" ~ yearDigits)
      .map { case (d, m, y) => ExactDate(d, m, y) }

  def dayMonthYear[_ : P] =
    (writtenDay ~ ws ~ writtenMonth ~ ws ~ yearDigits)
      .map { case (d, m, y) => ExactDate(d, m, y) }

  def monthDayYear[_ : P] =
    (writtenMonth ~ ws ~ writtenDay ~ ws ~ yearDigits)
      .map { case (m, d, y) => ExactDate(d, m, y) }

  def monthAndYear[_ : P] =
    (monthFollowedByYear | yearFollowedByMonth)
      .map { case (m, y) => MonthAndYear(m, y) }

  def month[_ : P] =
    writtenMonth.map(Month(_))

  def day[_ : P] =
    dayDigits.map(Day(_))

  def monthFollowedByYear[_ : P] =
    (writtenMonth ~ ws ~ yearDigits)

  def yearFollowedByMonth[_ : P] =
    (yearDigits ~ ws ~ writtenMonth) map { case (y, m) => (m, y) }

  def writtenDay[_ : P] = dayDigits ~ ordinalIndicator.?

  def writtenMonth[_ : P]  =
    StringInIgnoreCase(
      "january", "febuary", "march", "april", "may", "june",
      "july", "august", "september", "october", "november", "december",
      "jan", "feb", "mar", "apr", "may", "jun",
      "jul", "aug", "sep", "oct", "nov", "dec"
    ).!
    .map { name => monthMapping.get(name.toLowerCase.substring(0, 3)).get }

  val monthMapping = Map(
    "jan" -> 1, "feb" -> 2, "mar" -> 3, "apr" -> 4, "may" -> 5, "jun" -> 6,
    "jul" -> 7, "aug" -> 8, "sep" -> 9, "oct" -> 10, "nov" -> 11, "dec" -> 12,
  )

  def dayDigits[_ : P] =
    digit
      .rep(min=1, max=2).!
      .map(_.toInt)
      .filter(value => value >= 1 && value <= 31)

  def monthDigits[_ : P] =
    digit
      .rep(min=1, max=2).!
      .map(_.toInt)
      .filter(value => value >= 1 && value <= 12)

  def yearDigits[_ : P] =
    digit
      .rep(exactly=4).!
      .map(_.toInt)

  def ordinalIndicator[_ : P] = StringIn("st", "nd", "rd", "th")

  def digit[_ : P] = CharPred(_.isDigit)

  def ws[_ : P] = " ".rep

  def range[_ : P] = ws.? ~ "-" ~ ws.?

  def toInstantRange[_ : P, T <: TimePeriod : ToInstantRange](parser: P[T])
      : P[InstantRange] = 
    parser map (value => implicitly[ToInstantRange[T]].apply(value))
}
