package uk.ac.wellcome.platform.transformer.calm.periods

import fastparse._
import NoWhitespace._
import uk.ac.wellcome.models.parse._
import uk.ac.wellcome.models.work.internal.InstantRange

object PeriodParser extends Parser[InstantRange] with DateParserUtils {
  import DateParserImplicits._
  import FreeformDateParser.{calendarDate, day, month, monthAndYear}

  def parser[_: P]: P[InstantRange] = Start ~ timePeriod ~ End

  override def apply(input: String): Option[InstantRange] =
    super.apply(input.toLowerCase) map (_ withLabel input)

  def crumbs[_: P] = (inferredCentury to century)

  def timePeriod[_: P] =
    dateRange |
      calendarDate.toInstantRange |
      monthAndYear.toInstantRange |
      monthRangeYear.toInstantRange |
      century.toInstantRange |
      decade.toInstantRange |
      year.toInstantRange

  def dateRange[_: P] =
    (calendarDate to calendarDate).toInstantRange |
      (calendarDate to year).toInstantRange |
      (calendarDate to monthAndYear).toInstantRange |
      (calendarDate to monthAndDay).toInstantRange |
      (calendarDate to month).toInstantRange |
      (calendarDate to day).toInstantRange |
      (monthAndYear to calendarDate).toInstantRange |
      (monthAndYear to monthAndYear).toInstantRange |
      (monthAndYear to monthAndDay).toInstantRange |
      (monthAndYear to month).toInstantRange |
      (monthAndDay to calendarDate).toInstantRange |
      ((century | inferredCentury) to century).toInstantRange |
      (century to year).toInstantRange |
      (year to calendarDate).toInstantRange |
      (year to monthAndYear).toInstantRange |
      (year to century).toInstantRange |
      (year to year).toInstantRange |
      (monthRangeYear to monthRangeYear).toInstantRange |
      (decade to decade).toInstantRange |
      (month to monthAndYear).toInstantRange |
      (day to calendarDate).toInstantRange

  def inferredCentury[_: P]: P[Century] =
    P(Lex.int.filter(_ <= 999) ~ Lex.ordinalSuffix.? map { n =>
      Century(n - 1)
    })

  def century[_: P]: P[Century] = Lex.century map Century

  def decade[_: P]: P[CenturyAndDecade] = Lex.decade map { year =>
    CenturyAndDecade(century = year / 100, decade = (year % 100) / 10)
  }

  def monthAndDay[_: P]: P[MonthAndDay] =
    dayFollowedByMonth | monthFollowedByDay

  def dayFollowedByMonth[_: P]: P[MonthAndDay] =
    P(writtenDay ~ ws ~ writtenMonth map { case (d, m) => MonthAndDay(m, d) })

  def monthFollowedByDay[_: P]: P[MonthAndDay] =
    P(writtenMonth ~ ws ~ writtenDay map MonthAndDay.tupled)

  def monthRangeYear[_: P]: P[FuzzyDateRange[MonthAndYear, MonthAndYear]] =
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
