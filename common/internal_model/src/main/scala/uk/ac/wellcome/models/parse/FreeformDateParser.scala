package uk.ac.wellcome.models.parse

import java.time.LocalDate

import fastparse._, NoWhitespace._

/**
 *  Attempts to parse freeform text into a date or date range
 */
object FreeformDateParser extends Parser[FreeformDate] with DateParsingUtils {

  def parser[_ : P] =
    Start ~ (inferredDate | knownDate) ~ End

  def knownDate[_ : P] =
    (dateRange | fuzzyDate) map { FreeformDate(_) }

  def inferredDate[_ : P] =
    ("[".? ~ knownDate ~ "]") map (_ withInferred true)

  def fuzzyDate[_ : P] =
    FuzzyDateParser.parser map { Left(_) }

  def dateRange[_ : P] =
    DateRangeParser.parser map { Right(_) }
}

/**
 *  Attempts to parse freeform text into a date range
 */
object DateRangeParser extends Parser[DateRange] with DateParsingUtils {

  def parser[_ : P] =
    (monthRangeWithinAYear | monthRangeAcrossYears | yearRange | exactRange |
      dayRangeWithinAMonth)

  def yearRange[_ : P] =
    (FuzzyDateParser.year ~ range ~ FuzzyDateParser.year)
      .map { case (y1, y2) => DateRange(y1, y2) }

  def exactRange[_ : P] =
    (FuzzyDateParser.exactDate ~ range ~ FuzzyDateParser.exactDate)
      .map { case (d1, d2) => DateRange(d1, d2) }

  def monthRangeAcrossYears[_ : P] =
    (FuzzyDateParser.monthAndYear ~ range ~ FuzzyDateParser.monthAndYear)
      .map { case (my1, my2) => DateRange(my1, my2) }

  def monthRangeWithinAYear[_ : P] =
    (FuzzyDateParser.month ~ range ~ FuzzyDateParser.monthAndYear)
      .map { case (m, my) => DateRange(m, my) }

  def  dayRangeWithinAMonth[_ : P] =
    (FuzzyDateParser.day ~ range ~ FuzzyDateParser.exactDate)
      .map { case (a, b) => DateRange(a, b) }

  def range[_ : P] = ws.? ~ "-" ~ ws.?
}

/**
 *  Attempts to parse freeform text into a (potentially ambiguous) date
 */
object FuzzyDateParser extends Parser[FuzzyDate] with DateParsingUtils {

  def parser[_ : P] =
    year | exactDate | monthAndYear | month | day

  def year[_ : P] =
    yearDigits.map(Year(_))

  def exactDate[_ : P] =
    numericDate | dayMonthYear | monthDayYear

  def numericDate[_ : P] =
    (dayDigits ~ "/" ~ monthDigits ~ "/" ~ yearDigits)
      .map { case (d, m, y) => ExactDate(LocalDate of (y, m, d)) }

  def dayMonthYear[_ : P] =
    (writtenDay ~ ws ~ writtenMonth ~ ws ~ yearDigits)
      .map { case (d, m, y) => ExactDate(LocalDate of (y, m, d)) }

  def monthDayYear[_ : P] =
    (writtenMonth ~ ws ~ writtenDay ~ ws ~ yearDigits)
      .map { case (m, d, y) => ExactDate(LocalDate of (y, m, d)) }

  def monthAndYear[_ : P] =
    (monthFollowedByYear | yearFollowedByMonth)
      .map { case (m, y) => MonthAndYear(m, y) }

  def month[_ : P] =
    writtenMonth.map(Month(_))

  def day[_ : P] =
    dayDigits.map(Day(_))
}

/**
 *  Parsers for extracting various date info from a string
 */
protected trait DateParsingUtils {

  def monthFollowedByYear[_ : P] =
    (writtenMonth ~ ws ~ yearDigits) map { case (m, y) => (m, y) }

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
}
