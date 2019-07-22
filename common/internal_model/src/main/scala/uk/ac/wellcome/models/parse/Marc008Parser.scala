package uk.ac.wellcome.models.parse

import fastparse._, NoWhitespace._

import uk.ac.wellcome.models.work.internal.InstantRange
import DateParserImplicits._

/**
 *  Parsed Marc 008 fields into InstantRange
 *
 *  Spec: https://www.loc.gov/marc/bibliographic/bd008a.html
 */
object Marc008Parser extends Parser[InstantRange] with DateParserUtils {

  def parser[_ : P] = (Start ~ createdDate ~ timePeriod)

  def createdDate[_ : P] = CharPred(_.isDigit).rep(exactly=6)

  def timePeriod[_ : P] =
    singleKnownDate.toInstantRange |
      multipleDates.toInstantRange |
      publicationDateAndCopyrightDate.toInstantRange |
      detailedDate.toInstantRange

  def singleKnownDate[_ : P] =
    ("s" ~ yearDigits ~ emptyDate) map (Year(_))

  def multipleDates[_ : P] =
    ("m" ~ yearDigits ~ yearDigits)
      .map { case (from, to) => FuzzyDateRange(Year(from), Year(to)) }

  def publicationDateAndCopyrightDate[_ : P] =
    ("t" ~ yearDigits ~ yearDigits)
      .map { case (publicationDate, copyrightDate) => Year(publicationDate) }

  def detailedDate[_ : P] =
    ("e" ~ yearDigits ~ detailedDateMonth ~ detailedDateDay)
      .map { case (year, month, day) => CalendarDate(year, month, day) }

  def emptyDate[_ : P] = 
    " ".rep(exactly=4) | "u".rep(exactly=4) | "|".rep(exactly=4)

  def detailedDateMonth[_ : P] = digit.rep(exactly = 2).!.map(_.toInt)

  def detailedDateDay[_ : P] = digit.rep(exactly = 2).!.map(_.toInt)
}
