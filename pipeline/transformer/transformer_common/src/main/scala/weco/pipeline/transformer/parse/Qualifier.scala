package weco.pipeline.transformer.parse

import fastparse._
import NoWhitespace._

sealed trait Qualifier
object Qualifier {
  case object Pre extends Qualifier
  case object Post extends Qualifier
  case object About extends Qualifier // Covers about, approx, circa
  case object Mid extends Qualifier
  case object Early extends Qualifier
  case object Late extends Qualifier
  case object EarlyMid extends Qualifier
  case object MidLate extends Qualifier
}

trait QualifyFuzzyDate[D <: FuzzyDate] {
  val toDateRange: PartialFunction[(Qualifier, D), FuzzyDateRange[Year, Year]]
}

// Qualifier behaviours are specified here
// http://www.dswebhosting.info/documents/Manuals/ALM/V10/MANUAL/main_menu/basics/period_field_format.htm
object QualifyFuzzyDate extends ParserUtils {
  def qualified[_: P, D <: FuzzyDate](unqualified: => P[D])(
    implicit q: QualifyFuzzyDate[D]): P[FuzzyDateRange[Year, Year]] =
    (Lex.qualifier ~ ws.? ~ "-".? ~ ws.? ~ unqualified) collect q.toDateRange

  implicit val qualifyCentury =
    new QualifyFuzzyDate[Century] {
      lazy val toDateRange = {
        case (Qualifier.Early, Century(century)) =>
          FuzzyDateRange(
            Year(100 * century),
            Year(100 * century + 39),
          )
        case (Qualifier.Mid, Century(century)) =>
          FuzzyDateRange(
            Year(100 * century + 30),
            Year(100 * century + 69),
          )
        case (Qualifier.Late, Century(century)) =>
          FuzzyDateRange(
            Year(100 * century + 60),
            Year(100 * century + 99),
          )
        case (Qualifier.EarlyMid, Century(century)) =>
          FuzzyDateRange(
            Year(100 * century),
            Year(100 * century + 69),
          )
        case (Qualifier.MidLate, Century(century)) =>
          FuzzyDateRange(
            Year(100 * century + 30),
            Year(100 * century + 99),
          )
        case (Qualifier.About, Century(century)) =>
          FuzzyDateRange(
            Year(100 * century - 10),
            Year(100 * century + 109),
          )
      }
    }

  implicit val qualifyYear =
    new QualifyFuzzyDate[Year] {
      lazy val toDateRange = {
        case (Qualifier.About, Year(year)) =>
          FuzzyDateRange(
            Year(year - 10),
            Year(year + 9),
          )
        case (Qualifier.Pre, Year(year)) =>
          FuzzyDateRange(
            Year(year - 10),
            Year(year),
          )
        case (Qualifier.Post, Year(year)) =>
          FuzzyDateRange(
            Year(year),
            Year(year + 9),
          )
      }
    }

  implicit val qualifyDecade =
    new QualifyFuzzyDate[CenturyAndDecade] {
      lazy val toDateRange = {
        case (Qualifier.About, CenturyAndDecade(century, decade)) =>
          FuzzyDateRange(
            Year(century * 100 + (decade - 1) * 10),
            Year(century * 100 + (decade + 1) * 10),
          )
        case (Qualifier.Early, CenturyAndDecade(century, decade)) =>
          FuzzyDateRange(
            Year(century * 100 + decade * 10),
            Year(century * 100 + decade * 10 + 3)
          )
        case (Qualifier.Mid, CenturyAndDecade(century, decade)) =>
          FuzzyDateRange(
            Year(century * 100 + decade * 10 + 3),
            Year(century * 100 + decade * 10 + 6)
          )
        case (Qualifier.Late, CenturyAndDecade(century, decade)) =>
          FuzzyDateRange(
            Year(century * 100 + decade * 10 + 6),
            Year(century * 100 + decade * 10 + 9)
          )
      }
    }
}
