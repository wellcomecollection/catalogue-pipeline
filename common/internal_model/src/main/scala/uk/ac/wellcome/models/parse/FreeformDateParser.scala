package uk.ac.wellcome.models.parse

import uk.ac.wellcome.models.work.internal.InstantRange
import org.parboiled2._
import org.parboiled2.{Parser => ParboiledParser}

class FreeformDateParser(val input: ParserInput)
    extends ParboiledParser
    with DateHelpers {

  def parser = rule(inferredTimePeriod | timePeriod ~ EOI)

  def inferredTimePeriod =
    rule(("[".? ~ timePeriod ~ "]") ~> (_ withInferred true))

  def timePeriod =
    rule(
      dateRange |
        year().toInstantRange |
        calendarDate().toInstantRange |
        monthAndYear().toInstantRange)

  def dateRange =
    rule(
      (year to year).toInstantRange |
        (calendarDate to calendarDate).toInstantRange |
        (monthAndYear to monthAndYear).toInstantRange |
        (month to monthAndYear).toInstantRange |
        (day to calendarDate).toInstantRange)

  def calendarDate = () => rule(numericDate | dayMonthYear | monthDayYear)

  def numericDate =
    rule(
      (dayDigits ~ "/" ~ monthDigits ~ "/" ~ yearDigits)
        ~> ((day, month, year) => CalendarDate(day, month, year)))

  def dayMonthYear =
    rule(
      (writtenDay ~ ws ~ writtenMonth ~ ws ~ yearDigits)
        ~> ((day, month, year) => CalendarDate(day, month, year)))

  def monthDayYear =
    rule(
      (writtenMonth ~ ws ~ writtenDay ~ ws ~ yearDigits)
        ~> ((month, day, year) => CalendarDate(day, month, year)))

  def year = () => rule(yearDigits ~> (Year(_)))

  def month = () => rule(writtenMonth ~> (Month(_)))

  def day = () => rule(writtenDay ~> (Day(_)))

  def monthAndYear = () => rule(monthFollowedByYear | yearFollowedByMonth)

  def monthFollowedByYear =
    rule((writtenMonth ~ ws ~ yearDigits) ~> (MonthAndYear(_, _)))

  def yearFollowedByMonth =
    rule(
      (yearDigits ~ ws ~ writtenMonth) ~> ((year,
                                            month) =>
                                             MonthAndYear(month, year)))

  def writtenMonth = rule(valueMap(monthMapping, ignoreCase = true))

  def writtenDay = rule(dayDigits ~ ordinalIndicator.?)

  def yearDigits = rule(digits(from = 4, to = 4))

  def monthDigits =
    rule(
      digits(from = 1, to = 2)
        ~> (month => test(month >= 1 && month <= 12) ~ push(month)))

  def dayDigits =
    rule(
      digits(from = 1, to = 2)
        ~> (day => test(day >= 1 && day <= 31) ~ push(day)))

  def digits(from: Int, to: Int): Rule1[Int] =
    rule(
      capture(from.to(to).times(CharPredicate.Digit))
        ~> ((_: String).toInt))

  def ws = rule(oneOrMore(" "))

  def ordinalIndicator = rule("st" | "nd" | "rd" | "th")

  implicit class ToDateRangeParser[F <: FuzzyDate](from: () => Rule1[F]) {
    def to[T <: FuzzyDate](to: () => Rule1[T]): Rule1[FuzzyDateRange[F, T]] =
      rule(
        (from() ~ ws.? ~ "-" ~ ws.? ~ to())
          ~> ((f: F, t: T) => FuzzyDateRange(f, t)))
  }

  implicit class ToInstantRangeParser[T <: TimePeriod](parser: Rule1[T]) {
    def toInstantRange(
      implicit toInstantRange: ToInstantRange[T]): Rule1[InstantRange] =
      rule(
        parser
          ~> (toInstantRange.safeConvert(_))
          ~> (instantRange => test(instantRange.nonEmpty) ~ push(instantRange))
          ~> ((instantRange: Option[InstantRange]) => instantRange.get))
  }

  def monthMapping =
    Map(
      "jan" -> 1,
      "january" -> 1,
      "feb" -> 2,
      "february" -> 2,
      "mar" -> 3,
      "march" -> 3,
      "apr" -> 4,
      "april" -> 4,
      "may" -> 5,
      "jun" -> 6,
      "june" -> 6,
      "jul" -> 7,
      "july" -> 7,
      "aug" -> 8,
      "august" -> 8,
      "sep" -> 9,
      "september" -> 9,
      "oct" -> 10,
      "october" -> 10,
      "nov" -> 11,
      "november" -> 11,
      "dec" -> 12,
      "december" -> 12
    )
}
