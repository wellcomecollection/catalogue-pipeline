package uk.ac.wellcome.platform.transformer.calm.periods

import fastparse._
import NoWhitespace._
import uk.ac.wellcome.models.parse._
import uk.ac.wellcome.models.work.internal.InstantRange

object PeriodParser extends Parser[InstantRange] with DateParserUtils {
  import DateParserImplicits._
  import FreeformDateParser.{calendarDate, day, month, monthAndYear}
  import QualifyFuzzyDate._

  override def apply(input: String): Option[InstantRange] =
    super.apply(preprocess(input)) map (_ withLabel input)

  // These strings don't change parsing outcomes so we remove them rather
  // than trying to handle them in the parser
  private final val ignoreSubstrings = Seq(
    "\\[gaps\\]",
    "floruit",
    "fl\\.",
    "fl",
    "between",
    "\\(",
    "\\)",
    "\\[",
    "\\]",
    "\\?",
    "\\."
  )

  private def preprocess(input: String): String =
    ignoreSubstrings
      .reduceLeft(_ + "|" + _)
      .r
      .replaceAllIn(input.toLowerCase, "")
      .trim

  // Try to parse as many `timePeriod`s as we can and then add them to find
  // the interval that covers all of them
  def parser[_: P]: P[InstantRange] =
    (Start ~ timePeriod.rep(sep = multiPeriodSeparator, min = 1) ~ End) map {
      periods =>
        periods.reduceLeft(_ + _)
    }

  def multiPeriodSeparator[_: P]: P[Unit] =
    ws.? ~ StringIn(";", ",", "and") ~ ws.?

  def timePeriod[_: P] =
    dateRange |
      singleDate |
      (noopQualifier ~ ws.? ~ singleDate) // Try not to fail for un-spec'd qualifiers

  def singleDate[_: P]: P[InstantRange] =
    calendarDate.toInstantRange |
      monthAndYear.toInstantRange |
      yearDivision.toInstantRange |
      qualified(century).toInstantRange |
      century.toInstantRange |
      qualified(decade).toInstantRange |
      decade.toInstantRange |
      qualified(year).toInstantRange |
      yearRange.toInstantRange |
      year.toInstantRange

  def dateRange[_: P]: P[InstantRange] =
    calendarDateToDate |
      monthAndYearToDate |
      centuryToDate |
      yearToDate |
      decadeToDate |
      (monthAndDay to calendarDate).toInstantRange |
      (yearDivision to yearDivision).toInstantRange |
      (month to monthAndYear).toInstantRange |
      (day to calendarDate).toInstantRange

  def calendarDateToDate[_: P]: P[InstantRange] =
    (calendarDate to calendarDate).toInstantRange |
      (calendarDate to year).toInstantRange |
      (calendarDate to qualified(year)).toInstantRange |
      (calendarDate to monthAndYear).toInstantRange |
      (calendarDate to monthAndDay).toInstantRange |
      (calendarDate to month).toInstantRange |
      (calendarDate to day).toInstantRange

  def monthAndYearToDate[_: P]: P[InstantRange] =
    (monthAndYear to calendarDate).toInstantRange |
      (monthAndYear to monthAndYear).toInstantRange |
      (monthAndYear to monthAndDay).toInstantRange |
      (monthAndYear to month).toInstantRange |
      (monthAndYear to year).toInstantRange

  def centuryToDate[_: P]: P[InstantRange] =
    (qualified(century | inferredCentury) to century).toInstantRange |
      ((century | inferredCentury) to qualified(century)).toInstantRange |
      (qualified(century | inferredCentury) to qualified(century)).toInstantRange |
      ((century | inferredCentury) to century).toInstantRange |
      (century to decade).toInstantRange |
      (century to year).toInstantRange |
      (qualified(century) to year).toInstantRange |
      (century to qualified(year)).toInstantRange |
      (qualified(century) to qualified(year)).toInstantRange

  def yearToDate[_: P]: P[InstantRange] =
    (qualified(year) to calendarDate).toInstantRange |
      (qualified(year) to monthAndYear).toInstantRange |
      (year to calendarDate).toInstantRange |
      (year to monthAndYear).toInstantRange |
      (year to century).toInstantRange |
      (qualified(year) to century).toInstantRange |
      (year to qualified(century)).toInstantRange |
      (qualified(year) to qualified(century)).toInstantRange |
      (qualified(year) to qualified(decade)).toInstantRange |
      (qualified(year) to decade).toInstantRange |
      (year to qualified(decade)).toInstantRange |
      (year to decade).toInstantRange |
      (year to year).toInstantRange |
      (qualified(year) to year).toInstantRange |
      (year to qualified(year)).toInstantRange |
      (qualified(year) to qualified(year)).toInstantRange

  def decadeToDate[_: P]: P[InstantRange] =
    (qualified(decade) to qualified(decade)).toInstantRange |
      (qualified(decade) to decade).toInstantRange |
      (decade to qualified(decade)).toInstantRange |
      (decade to decade).toInstantRange |
      (qualified(decade) to qualified(year)).toInstantRange |
      (qualified(decade) to year).toInstantRange |
      (decade to qualified(year)).toInstantRange |
      (decade to year).toInstantRange

  def noopQualifier[_: P]: P[Unit] = Lex.qualifier map (_ => ())

  // We can infer a century intention for some numbers, eg for
  // 14 in the string "14th-15th century"
  def inferredCentury[_: P]: P[Century] =
    P(Lex.int.filter(_ <= 999) ~ Lex.ordinalSuffix.? map { n =>
      Century(n - 1)
    })

  def century[_: P]: P[Century] = Lex.century map Century

  def decade[_: P]: P[CenturyAndDecade] = Lex.decade map { year =>
    CenturyAndDecade(century = year / 100, decade = (year % 100) / 10)
  }

  // A year range is a string like 1994-5 or 1066-90
  def yearRange[_: P]: P[FuzzyDateRange[Year, Year]] =
    P(year ~ ws.? ~ "-" ~ ws.? ~ digitRep(1, 2) map {
      case (year @ Year(y), n) if n < 10 =>
        FuzzyDateRange(year, Year(y - (y % 10) + n))
      case (year @ Year(y), n) if n < 100 =>
        FuzzyDateRange(year, Year(y - (y % 100) + n))
    })

  def monthAndDay[_: P]: P[MonthAndDay] =
    dayFollowedByMonth | monthFollowedByDay

  def dayFollowedByMonth[_: P]: P[MonthAndDay] =
    P(writtenDay ~ ws ~ writtenMonth map { case (d, m) => MonthAndDay(m, d) })

  def monthFollowedByDay[_: P]: P[MonthAndDay] =
    P(writtenMonth ~ ws ~ writtenDay map MonthAndDay.tupled)

  def yearDivision[_: P]: P[FuzzyDateRange[MonthAndYear, MonthAndYear]] =
    seasonYear | lawTermYear

  def seasonYear[_: P]: P[FuzzyDateRange[MonthAndYear, MonthAndYear]] =
    P((Lex.season map {
      case "spring" => (3, 5)
      case "summer" => (6, 8)
      case "autumn" => (9, 11)
      // Winter YEAR refers to the year in which the winter starts
      // https://www.metoffice.gov.uk/weather/learn-about/weather/seasons/winter/when-does-winter-start
      case "winter" => (12, 2)
    }) ~ ws ~ yearDigits map {
      case (fromMonth, toMonth, year) =>
        FuzzyDateRange(
          MonthAndYear(fromMonth, year),
          MonthAndYear(toMonth, if (fromMonth < toMonth) year else year + 1)
        )
    })

  def lawTermYear[_: P]: P[FuzzyDateRange[MonthAndYear, MonthAndYear]] =
    P((Lex.lawTerm map {
      case "michaelmas" => (10, 11)
      case "hilary"     => (1, 2)
      case "easter"     => (4, 5)
      case "trinity"    => (6, 7)
    }) ~ ws ~ yearDigits map {
      case (fromMonth, toMonth, year) =>
        FuzzyDateRange(
          MonthAndYear(fromMonth, year),
          MonthAndYear(toMonth, year)
        )
    })

}
