package uk.ac.wellcome.models.parse

import uk.ac.wellcome.models.work.internal.InstantRange
import fastparse._, NoWhitespace._

trait DateParserUtils extends ParserUtils {

  def monthFollowedByYear[_: P] =
    (writtenMonth ~ ws ~ yearDigits)

  def yearFollowedByMonth[_: P] =
    (yearDigits ~ ws ~ writtenMonth) map { case (y, m) => (m, y) }

  def writtenDay[_: P] = dayDigits ~ ordinalIndicator.?

  def writtenMonth[_: P] =
    StringInIgnoreCase(
      "january",
      "febuary",
      "march",
      "april",
      "may",
      "june",
      "july",
      "august",
      "september",
      "october",
      "november",
      "december",
      "jan",
      "feb",
      "mar",
      "apr",
      "may",
      "jun",
      "jul",
      "aug",
      "sep",
      "oct",
      "nov",
      "dec"
    ).!.map { name =>
      monthMapping.get(name.toLowerCase.substring(0, 3)).get
    }

  val monthMapping = Map(
    "jan" -> 1,
    "feb" -> 2,
    "mar" -> 3,
    "apr" -> 4,
    "may" -> 5,
    "jun" -> 6,
    "jul" -> 7,
    "aug" -> 8,
    "sep" -> 9,
    "oct" -> 10,
    "nov" -> 11,
    "dec" -> 12,
  )

  def year[_: P] = yearDigits map (Year(_))

  def dayDigits[_: P] =
    digitRep(min = 1, max = 2).filter(value => value >= 1 && value <= 31)

  def monthDigits[_: P] =
    digitRep(min = 1, max = 2).filter(value => value >= 1 && value <= 12)

  def yearDigits[_: P] = digitRep(exactly = 4)

  def ordinalIndicator[_: P] = StringIn("st", "nd", "rd", "th")

}

trait ParserUtils {

  def digit[_: P] = CharPred(_.isDigit)

  def digitRep[_ : P](min: Int, max: Int) =
    digit
      .rep(min = min, max = max)
      .!
      .map(_.toInt)

  def digitRep[_ : P](exactly: Int) =
    digit
      .rep(exactly = exactly)
      .!
      .map(_.toInt)

  def ws[_: P] = " ".rep
}

/**
  *  Implicit classes used for conversion of P[A] to P[B] for some A / B
  */
object DateParserImplicits extends ParserUtils {

  implicit class ToDateRangeParser[F <: FuzzyDate](from: => P[F]) {

    def to[_: P, T <: FuzzyDate](to: => P[T]): P[FuzzyDateRange[F, T]] =
      (from ~ ws.? ~ "-" ~ ws.? ~ to)
        .map { case (f, t) => FuzzyDateRange(f, t) }
  }

  implicit class ToInstantRangeParser[T <: TimePeriod](parser: P[T]) {

    def toInstantRange[_: P](
      implicit toInstantRange: ToInstantRange[T]): P[InstantRange] =
      parser
        .map(toInstantRange.safeConvert(_))
        .filter(_.nonEmpty)
        .map(_.get)
  }
}
