package weco.pipeline.transformer.parse

import fastparse._
import NoWhitespace._
import weco.catalogue.internal_model.work.InstantRange

trait DateParserUtils extends ParserUtils {

  def monthFollowedByYear[_: P] =
    writtenMonth ~ ws ~ yearDigits

  def yearFollowedByMonth[_: P] =
    (yearDigits ~ ws ~ writtenMonth) map { case (y, m) => (m, y) }

  def writtenDay[_: P] = dayDigits ~ ordinalIndicator.?

  def writtenMonth[_: P] =
    StringInIgnoreCase(
      "january",
      "february",
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

  def yearDigits[_: P] =
    digitRep(exactly = 4) ~ (ws.? ~ era).? map {
      case (year, Some("bc")) => -year
      case (year, _)          => year
    }

  def era[_: P] =
    P(StringInIgnoreCase("a.d.", "ad", "b.c.", "bc").! map (_.toLowerCase) map {
      case "a.d." | "ad" => "ad"
      case "b.c." | "bc" => "bc"
    })

  def ordinalIndicator[_: P] = StringIn("st", "nd", "rd", "th")

}

trait ParserUtils {

  def digit[_: P] = CharPred(_.isDigit)

  def digitRep[_: P](min: Int, max: Int) =
    digit
      .rep(min = min, max = max)
      .!
      .map(_.toInt)

  def digitRep[_: P](exactly: Int) =
    digit
      .rep(exactly = exactly)
      .!
      .map(_.toInt)

  def ws[_: P] = " ".rep

  // EagerOps is defined here:
  // https://github.com/lihaoyi/fastparse/blob/dd74612224846d3743e19419b3f1191554b973f5/fastparse/src/fastparse/package.scala#L119
  implicit class AdditionalEagerOps[T](val parse: P[T]) {
    def collect[V](pf: PartialFunction[T, V])(implicit ctx: P[Any]): P[V] =
      parse.filter(pf.isDefinedAt).map(pf.apply)

    def flatMapOption[V](f: T => Option[V])(implicit ctx: P[Any]): P[V] =
      parse.map(f).collect { case Some(v) => v }
  }
}

/**
  *  Implicit classes used for conversion of P[A] to P[B] for some A / B
  */
object DateParserImplicits extends ParserUtils {

  // Unicode 0096 is used as a separator in some data
  // It is the "START OF GUARDED AREA" character
  private def sep[_: P]: P[Unit] =
    P(ws.? ~ StringIn("to", "x", "-", "/", "\u0096") ~ ws.?)

  implicit class DateToDateRangeParser[F <: FuzzyDate](from: => P[F]) {

    def to[_: P, T <: FuzzyDate](to: => P[T]): P[FuzzyDateRange[F, T]] =
      (from ~ sep ~ to)
        .map { case (f, t) => FuzzyDateRange(f, t) }

    // The DummyImplicit is to prevent type erasure causing these methods
    // to have duplicate signatures.
    def to[_: P, T <: FuzzyDate](to: => P[FuzzyDateRange[_ <: FuzzyDate, T]])(
      implicit d: DummyImplicit): P[FuzzyDateRange[F, T]] =
      (from ~ sep ~ to)
        .map {
          case (f, FuzzyDateRange(_, t)) => FuzzyDateRange(f, t)
        }
  }

  implicit class DateRangeToDateRangeParser[F <: FuzzyDate](
    from: => P[FuzzyDateRange[F, _ <: FuzzyDate]]) {

    def to[_: P, T <: FuzzyDate](to: => P[T]): P[FuzzyDateRange[F, T]] =
      (from ~ sep ~ to)
        .map {
          case (FuzzyDateRange(f, _), t) => FuzzyDateRange(f, t)
        }

    // The DummyImplicit is to prevent type erasure causing these methods
    // to have duplicate signatures.
    def to[_: P, T <: FuzzyDate](to: => P[FuzzyDateRange[_ <: FuzzyDate, T]])(
      implicit d: DummyImplicit): P[FuzzyDateRange[F, T]] =
      (from ~ sep ~ to)
        .map {
          case (FuzzyDateRange(f, _), FuzzyDateRange(_, t)) =>
            FuzzyDateRange(f, t)
        }
  }

  implicit class ToInstantRangeParser[T <: TimePeriod](parser: P[T]) {

    def toInstantRange[_: P](
      implicit toInstantRange: ToInstantRange[T]): P[InstantRange] =
      parser.flatMapOption(toInstantRange.safeConvert)
  }
}
