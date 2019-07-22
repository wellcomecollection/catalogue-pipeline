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

  def createdDate[_ : P] = AnyChar.rep(exactly=6)

  def timePeriod[_ : P] =
    singleKnownDate |
      multipleDates.toInstantRange |
      detailedDate.toInstantRange |
      publicationDateAndCopyrightDate |
      reprintDate |
      continuingResourceCeasedPublication.toInstantRange |
      continuingResourceCurrentlyPublished.toInstantRange |
      continuingResourceStatusUnknown.toInstantRange |
      questionableDate.toInstantRange |
      differingReleaseAndProduction.toInstantRange

  def singleKnownDate[_ : P] =
    ("s" ~ partialYear ~ emptyDate)

  def multipleDates[_ : P] =
    ("m" ~ year ~ year) map { case (from, to) => FuzzyDateRange(from, to) }

  def detailedDate[_ : P] =
    ("e" ~ yearDigits ~ digitRep(exactly = 2) ~ digitRep(exactly = 2))
      .map { case (year, month, day) => CalendarDate(day, month, year) }

  // TODO : should we store the publication date or copyright date?
  def publicationDateAndCopyrightDate[_ : P] =
    ("t" ~ partialYear ~ partialYear)
      .map { case (pubYear, copyYear) => pubYear }

  // TODO : should we store the reprint date or original date?
  def reprintDate[_ : P] =
    ("r" ~ partialYear ~ partialYear)
      .map { case (reprintYear, originalYear) => reprintYear }

  def continuingResourceCeasedPublication[_ : P] =
    ("d" ~ year ~ year) map { case (from, to) => FuzzyDateRange(from, to) }

  // TODO : should we be using final year 9999 here?
  def continuingResourceCurrentlyPublished[_ : P] =
    ("c" ~ year ~ "9999") map (FuzzyDateRange(_, Year(9999)))

  // TODO : should we be using final year 9999 here?
  def continuingResourceStatusUnknown[_ : P] =
    ("u" ~ year ~ "uuuu") map (FuzzyDateRange(_, Year(9999)))

  // TODO : should these dates be marked as inferred?
  def questionableDate[_ : P] =
    ("q" ~ year ~ year) map { case (from, to) => FuzzyDateRange(from, to) }

  // TODO : should we be using release or production date?
  def differingReleaseAndProduction[_ : P] =
    ("p" ~ year ~ year) map { case (release, production) => release }

  // TODO : should century / century and decade be marked inferred
  def partialYear[_ : P] =
    year.toInstantRange |
      century.toInstantRange |
      centuryAndDecade.toInstantRange

  def century[_ : P] =
    (digitRep(exactly = 2) ~ "uu") map (Century(_))

  def centuryAndDecade[_ : P] = 
    (digitRep(exactly = 2) ~ digitRep(exactly = 1) ~ "u")
      .map { case  (century, decade) => CenturyAndDecade(century, decade) }

  def emptyDate[_ : P] = CharIn(" u|#").rep(exactly=4)
}
