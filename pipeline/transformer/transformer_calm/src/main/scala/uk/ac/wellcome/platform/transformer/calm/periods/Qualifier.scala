package uk.ac.wellcome.platform.transformer.calm.periods

import fastparse._
import NoWhitespace._
import uk.ac.wellcome.models.parse._

sealed trait Qualifier
sealed trait CenturyQualifier extends Qualifier
sealed trait YearQualifier extends Qualifier
object Qualifier {
  case object Pre extends YearQualifier
  case object Post extends YearQualifier
  case object About extends YearQualifier // Covers about, approx, circa
  case object Mid extends CenturyQualifier
  case object Early extends CenturyQualifier
  case object Late extends CenturyQualifier
}

trait QualifyFuzzyDate[D <: FuzzyDate, Q <: Qualifier] {
  def parse[_: P]: P[Q]
  def toDateRange(qualifier: Q, date: D): FuzzyDateRange[Year, Year]
}

object QualifyFuzzyDate extends ParserUtils {
  def qualified[_: P, D <: FuzzyDate, Q <: Qualifier](unqualified: => P[D])(
    implicit q: QualifyFuzzyDate[D, Q]): P[FuzzyDateRange[Year, Year]] =
    (q.parse ~ ws.? ~ unqualified) map {
      case (qualifier, date) => q.toDateRange(qualifier, date)
    }

  implicit val qualifyCentury =
    new QualifyFuzzyDate[Century, CenturyQualifier] {
      def parse[_: P]: P[CenturyQualifier] = Lex.early | Lex.mid | Lex.late
      def toDateRange(qualifier: CenturyQualifier,
                      date: Century): FuzzyDateRange[Year, Year] =
        (qualifier, date) match {
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
        }
    }

  implicit val qualifyYear =
    new QualifyFuzzyDate[Year, YearQualifier] {
      def parse[_: P]: P[YearQualifier] = Lex.about | Lex.pre | Lex.post
      def toDateRange(qualifier: YearQualifier,
                      date: Year): FuzzyDateRange[Year, Year] =
        (qualifier, date) match {
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
}
