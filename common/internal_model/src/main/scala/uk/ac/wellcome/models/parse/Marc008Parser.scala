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
    singleKnownDate |
      multipleDates.toInstantRange |
      publicationDateAndCopyrightDate |
      detailedDate.toInstantRange

  def singleKnownDate[_ : P] =
    ("s" ~ partialYear ~ emptyDate)

  def multipleDates[_ : P] =
    ("m" ~ year ~ year) map { case (from, to) => FuzzyDateRange(from, to) }

  def publicationDateAndCopyrightDate[_ : P] =
    ("t" ~ partialYear ~ partialYear)
      .map { case (pubYear, copyYear) => pubYear }

  def detailedDate[_ : P] =
    ("e" ~ yearDigits ~ digitRep(exactly = 2) ~ digitRep(exactly = 2))
      .map { case (year, month, day) => CalendarDate(year, month, day) }

  def partialYear[_ : P] =
    year.toInstantRange |
      century.toInstantRange |
      centuryAndDecade.toInstantRange

  def century[_ : P] =
    (digitRep(exactly = 2) ~ "u".rep(exactly=2)) map (Century(_))

  def centuryAndDecade[_ : P] = 
    (digitRep(exactly = 2) ~ digitRep(exactly = 1) ~ "u")
      .map { case  (century, decade) => CenturyAndDecade(century, decade) }

  def emptyDate[_ : P] = 
    " ".rep(exactly=4) | "u".rep(exactly=4) | "|".rep(exactly=4)
}
