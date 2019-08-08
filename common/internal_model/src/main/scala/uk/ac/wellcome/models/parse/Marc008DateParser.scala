package uk.ac.wellcome.models.parse

import fastparse._, NoWhitespace._

import uk.ac.wellcome.models.work.internal.InstantRange
import DateParserImplicits._

/**
  *  Parses Marc 008 date information into InstantRange
  *
  *  Spec: https://www.loc.gov/marc/bibliographic/bd008a.html
  */
object Marc008DateParser extends Parser[InstantRange] with DateParserUtils {

  def parser[_: P] =
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

  def singleKnownDate[_: P] =
    ("s" ~ partialYear ~ emptyDate)

  def multipleDates[_: P] =
    ("m" ~ year ~ year) map { case (from, to) => FuzzyDateRange(from, to) }

  def detailedDate[_: P] =
    ("e" ~ yearDigits ~ digitRep(exactly = 2) ~ digitRep(exactly = 2))
      .map { case (year, month, day) => CalendarDate(day, month, year) }

  def publicationDateAndCopyrightDate[_: P] =
    ("t" ~ partialYear ~ partialYear)
      .map { case (pubYear, copyYear) => pubYear }

  def reprintDate[_: P] =
    ("r" ~ partialYear ~ partialYear)
      .map { case (reprintYear, originalYear) => reprintYear }

  def continuingResourceCeasedPublication[_: P] =
    ("d" ~ year ~ year) map { case (from, to) => FuzzyDateRange(from, to) }

  // TODO : InstantRange should have optional start / end rather than 9999
  def continuingResourceCurrentlyPublished[_: P] =
    ("c" ~ year ~ "9999") map (FuzzyDateRange(_, Year(9999)))

  // TODO : InstantRange should have optional start / end rather than 9999
  def continuingResourceStatusUnknown[_: P] =
    ("u" ~ year ~ "uuuu") map (FuzzyDateRange(_, Year(9999)))

  // TODO : should these dates be marked as inferred?
  def questionableDate[_: P] =
    ("q" ~ year ~ year) map { case (from, to) => FuzzyDateRange(from, to) }

  def differingReleaseAndProduction[_: P] =
    ("p" ~ year ~ year) map { case (release, production) => release }

  // TODO : should century / century and decade be marked inferred
  def partialYear[_: P] =
    year.toInstantRange |
      century.toInstantRange |
      centuryAndDecade.toInstantRange

  def century[_: P] =
    (digitRep(exactly = 2) ~ "uu") map (Century(_))

  def centuryAndDecade[_: P] =
    (digitRep(exactly = 2) ~ digitRep(exactly = 1) ~ "u")
      .map { case (century, decade) => CenturyAndDecade(century, decade) }

  def emptyDate[_: P] = CharIn(" u|#").rep(exactly = 4)
}
