package weco.pipeline.transformer.parse

import fastparse._
import NoWhitespace._
import weco.catalogue.internal_model.work.InstantRange

object PeriodParser extends Parser[InstantRange] with DateParserUtils {
  import DateParserImplicits._
  import QualifyFuzzyDate._

  override def apply(input: String): Option[InstantRange] =
    super.apply(preprocess(input)) map (_ withLabel input)

  // These strings don't change parsing outcomes so we remove them rather
  // than trying to handle them in the parser
  private final lazy val ignoreRegex = Seq(
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
    "\\.",
    "\"",
    "©",
    // roman numerals are almost always accompanied by a decimal
    // date, so strip them out if they're at the start of a date
    // and followed by a word boundary.
    s"""^"?$romanNumeralRegexString\\b"""
  ).reduceLeft(_ + "|" + _).r

  // This is from:
  // https://www.oreilly.com/library/view/regular-expressions-cookbook/9780596802837/ch06s09.html
  // It is additionally restricted to numerals 3 characters or longer,
  // to avoid interfering with things like (eg) 'c.1920'", and includes
  // period and comma separators to handle numerals like "M.DCC.XXXVII."
  private val romanNumeralRegexString = {
    val sep = "[\\.\\,]?\\s?"
    s"(?=[MDCLXVI\\.\\,\\s]{3,})M*$sep(C[MD]|D?C*)$sep(X[CL]|L?X*)$sep(I[XV]|V?I*)".toLowerCase
  }

  def preprocess(input: String): String =
    ignoreRegex
      .replaceAllIn(input.toLowerCase, "")
      .trim

  // Try to parse as many `timePeriod`s as we can and then add them to find
  // the interval that covers all of them
  def parser[_: P]: P[InstantRange] =
    ((Start ~ timePeriod.rep(sep = multiPeriodSeparator, min = 1) ~ End) map {
      periods =>
        periods.reduceLeft(_ + _)
    }) |
      (Start ~ halfBoundedDate ~ End)

  // Any separator between several periods
  // eg: "25th December 1956; 1957, 1959"
  private def multiPeriodSeparator[_: P]: P[Unit] =
    ws.? ~ StringIn(";", ",", "and") ~ ws.?

  // Any bounded time period: a range or a single date
  private def timePeriod[_: P]: P[InstantRange] =
    dateRange |
      singleDate |
      (noopQualifier ~ ws.? ~ singleDate) // Try not to fail for un-spec'd qualifiers

  // Any half-bounded time period: before or after a date
  private def halfBoundedDate[_: P]: P[InstantRange] =
    (afterDate map InstantRange.after) |
      (beforeDate map InstantRange.before)

  // eg "-1999" or "before 2001"
  private def beforeDate[_: P]: P[InstantRange] =
    ("-" ~ ws.? ~ singleDate) | ("before" ~ ws.? ~ singleDate)

  // eg "1917-" or "after 1800"
  private def afterDate[_: P]: P[InstantRange] =
    (singleDate ~ ws.? ~ "-") | ("after" ~ ws.? ~ singleDate)

  // Any single date, whether that's an exact date or
  // a month/year/century/decade/etc
  private def singleDate[_: P]: P[InstantRange] =
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

  // Any range of dates; effectively a timespan between
  // 2 single dates (whether these are exact or not)
  private def dateRange[_: P]: P[InstantRange] =
    calendarDateToDate |
      monthAndYearToDate |
      centuryToDate |
      yearToDate |
      decadeToDate |
      (monthAndDay to calendarDate).toInstantRange |
      (yearDivision to yearDivision).toInstantRange |
      (month to monthAndYear).toInstantRange |
      (day to calendarDate).toInstantRange

  // A range of dates beginning with a calendar date
  private def calendarDateToDate[_: P]: P[InstantRange] =
    (calendarDate to calendarDate).toInstantRange |
      (calendarDate to year).toInstantRange |
      (calendarDate to qualified(year)).toInstantRange |
      (calendarDate to monthAndYear).toInstantRange |
      (calendarDate to monthAndDay).toInstantRange |
      (calendarDate to month).toInstantRange |
      (calendarDate to day).toInstantRange

  // A range of dates dates beginning with a month and year
  private def monthAndYearToDate[_: P]: P[InstantRange] =
    (monthAndYear to calendarDate).toInstantRange |
      (monthAndYear to monthAndYear).toInstantRange |
      (monthAndYear to monthAndDay).toInstantRange |
      (monthAndYear to month).toInstantRange |
      (monthAndYear to year).toInstantRange

  // A range of dates beginning with a century
  private def centuryToDate[_: P]: P[InstantRange] =
    (qualified(century | inferredCentury) to century).toInstantRange |
      ((century | inferredCentury) to qualified(century)).toInstantRange |
      (qualified(century | inferredCentury) to qualified(century)).toInstantRange |
      ((century | inferredCentury) to century).toInstantRange |
      (century to decade).toInstantRange |
      (century to year).toInstantRange |
      (qualified(century) to year).toInstantRange |
      (century to qualified(year)).toInstantRange |
      (qualified(century) to qualified(year)).toInstantRange

  // A range of dates beginning with a year
  private def yearToDate[_: P]: P[InstantRange] =
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

  // A range of dates beginning with a decade
  private def decadeToDate[_: P]: P[InstantRange] =
    (qualified(decade) to qualified(decade)).toInstantRange |
      (qualified(decade) to decade).toInstantRange |
      (decade to qualified(decade)).toInstantRange |
      (decade to decade).toInstantRange |
      (qualified(decade) to qualified(year)).toInstantRange |
      (qualified(decade) to year).toInstantRange |
      (decade to qualified(year)).toInstantRange |
      (decade to year).toInstantRange

  // A qualifier which we don't know how to use and so make it a no-op
  private def noopQualifier[_: P]: P[Unit] = Lex.qualifier map (_ => ())

  // We can infer a century intention for some numbers, eg for
  // 14 in the string "14th-15th century"
  private def inferredCentury[_: P]: P[Century] =
    P(Lex.int.filter(_ <= 999) ~ Lex.ordinalSuffix.? map { n =>
      Century(n - 1)
    })

  // eg "1800s"
  private def century[_: P]: P[Century] = Lex.century map Century

  // eg "1920s"
  private def decade[_: P]: P[CenturyAndDecade] = Lex.decade map { year =>
    CenturyAndDecade(century = year / 100, decade = (year % 100) / 10)
  }

  // A year range is a string like 1994-5 or 1066-90
  private def yearRange[_: P]: P[FuzzyDateRange[Year, Year]] =
    P(year ~ ws.? ~ "-" ~ ws.? ~ digitRep(1, 2) map {
      case (year @ Year(y), n) if n < 10 =>
        FuzzyDateRange(year, Year(y - (y % 10) + n))
      case (year @ Year(y), n) if n < 100 =>
        FuzzyDateRange(year, Year(y - (y % 100) + n))
    })

  // eg "January 12th" or "15th May"
  private def monthAndDay[_: P]: P[MonthAndDay] =
    dayFollowedByMonth | monthFollowedByDay

  // eg "11th May"
  private def dayFollowedByMonth[_: P]: P[MonthAndDay] =
    P(writtenDay ~ ws ~ writtenMonth map { case (d, m) => MonthAndDay(m, d) })

  // eg "December 24th"
  private def monthFollowedByDay[_: P]: P[MonthAndDay] =
    P(writtenMonth ~ ws ~ writtenDay map MonthAndDay.tupled)

  // A standardised section of a year, for example a season
  private def yearDivision[_: P]
    : P[FuzzyDateRange[MonthAndYear, MonthAndYear]] =
    seasonYear | lawTermYear

  // Any specified day/date/month
  private def calendarDate[_: P]: P[CalendarDate] =
    numericDate | dayMonthYear | monthDayYear | yearMonthDay

  // eg "31/12/2013"
  private def numericDate[_: P]: P[CalendarDate] =
    (dayDigits ~ "/" ~ monthDigits ~ "/" ~ yearDigits)
      .map { case (d, m, y) => CalendarDate(d, m, y) }

  // eg "12th December 1992" or "12th 3 1888"
  private def dayMonthYear[_: P]: P[CalendarDate] =
    (writtenDay ~ ws ~ (writtenMonth | monthDigits) ~ ",".? ~ ws ~ yearDigits)
      .map { case (d, m, y) => CalendarDate(d, m, y) }

  // eg "January 12th 1567"
  private def monthDayYear[_: P]: P[CalendarDate] =
    (writtenMonth ~ ws ~ writtenDay ~ ",".? ~ ws ~ yearDigits)
      .map { case (m, d, y) => CalendarDate(d, m, y) }

  // eg "1801 March 19th"
  private def yearMonthDay[_: P]: P[CalendarDate] =
    (yearDigits ~ ws ~ (writtenMonth | monthDigits) ~ ws ~ writtenDay).map {
      case (y, m, d) => CalendarDate(d, m, y)
    }

  // eg "January 2012" or "2013 December"
  private def monthAndYear[_: P]: P[MonthAndYear] =
    (monthFollowedByYear | yearFollowedByMonth)
      .map { case (m, y) => MonthAndYear(m, y) }

  // eg "June"
  private def month[_: P]: P[Month] = writtenMonth map Month

  // An ordinal which we can use as a day-of-month
  // eg "31st"
  private def day[_: P]: P[Day] = writtenDay map Day

  // eg "spring"
  private def seasonYear[_: P]: P[FuzzyDateRange[MonthAndYear, MonthAndYear]] =
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

  // eg "michaelmas"
  private def lawTermYear[_: P]: P[FuzzyDateRange[MonthAndYear, MonthAndYear]] =
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
