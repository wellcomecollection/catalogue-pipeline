package uk.ac.wellcome.platform.transformer.calm.periods

import fastparse._
import SingleLineWhitespace._
import uk.ac.wellcome.models.parse.Parser

import scala.Function.const

trait Helpers {
  def keyword[T, _: P](token: T, str: String): P[T] =
    LiteralStr(str) map const(token)
  def keywords[T, _: P](token: T, p: P[_]): P[T] =
    p map const(token)
}

object ElementLexer extends Helpers {
  def ordinalSuffix[_: P]: P[Unit] = StringIn("th", "rd", "st", "nd")
  def int[_: P]: P[Int] =
    P(CharIn("0-9").repX(1).!.map(_.toInt))
  def numericCentury[_: P]: P[CENTURY] =
    P(int ~ "s" filter (_ % 100 == 0) map { year =>
      CENTURY((year / 100) + 1)
    })
  def textCentury[_: P]: P[CENTURY] =
    P(int ~ ordinalSuffix.? ~ StringIn("century", "cent.", "cent") map CENTURY)
  def century[_: P]: P[CENTURY] = P(numericCentury | textCentury)
  def decade[_: P]: P[DECADE] = P(int ~ "s" filter (_ % 10 == 0) map DECADE)
  def month[_: P]: P[MONTH] =
    P(
      StringIn(
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
      ).! map { month =>
        MONTH(month.slice(0, 3))
      })
  def year[_: P]: P[YEAR] =
    P(CharIn("0-9").repX(exactly = 4).! map (_.toInt) map YEAR)
  def ordinal[_: P]: P[ORDINAL] =
    P(int ~ ordinalSuffix map ORDINAL)
  def number[_: P]: P[NUMBER] = P(int map NUMBER)
  def season[_: P]: P[SEASON] =
    P(StringIn("spring", "summer", "autumn", "fall", "winter").! map SEASON)
  def lawTerm[_: P]: P[LAWTERM] =
    P(StringIn("michaelmas", "hilary", "easter", "trinity").! map LAWTERM)
  def noDate[_: P]: P[NODATE.type] =
    keywords(NODATE, StringIn("n.d.", "undated", "unknown"))
  def present[_: P]: P[PRESENT.type] = keyword(PRESENT, "present")
  def slash[_: P]: P[SLASH.type] = keyword(SLASH, "/")
  def rangeSeparator[_: P]: P[RANGESEPARATOR.type] =
    keywords(RANGESEPARATOR, StringIn("between", "to", "x", "-"))

  def token[_: P]: P[ElementToken] =
    P(
      century | decade | ordinal | month | year | number | slash | season | lawTerm | noDate | present | rangeSeparator
    )
}

private object QualifierLexer extends Helpers {
  def pre[_: P]: P[QUALIFIER.PRE.type] = keyword(QUALIFIER.PRE, "pre")
  def post[_: P]: P[QUALIFIER.POST.type] = keyword(QUALIFIER.POST, "post")
  def mid[_: P]: P[QUALIFIER.MID.type] =
    keywords(QUALIFIER.MID, StringIn("middle", "mid.", "mid"))
  def early[_: P]: P[QUALIFIER.EARLY.type] = keyword(QUALIFIER.EARLY, "early")
  def late[_: P]: P[QUALIFIER.LATE.type] = keyword(QUALIFIER.LATE, "late")
  def about[_: P]: P[QUALIFIER.ABOUT.type] = keyword(QUALIFIER.ABOUT, "about")
  def approx[_: P]: P[QUALIFIER.APPROX.type] =
    keyword(QUALIFIER.APPROX, "approx")
  def between[_: P]: P[QUALIFIER.BETWEEN.type] =
    keyword(QUALIFIER.BETWEEN, "between")
  def circa[_: P]: P[QUALIFIER.CIRCA.type] =
    keywords(QUALIFIER.CIRCA, StringIn("circa", "circ.", "circ", "c.", "c"))
  def floruit[_: P]: P[QUALIFIER.FLORUIT.type] =
    keywords(QUALIFIER.FLORUIT, StringIn("floruit", "fl.", "fl"))
  def era[_: P]: P[QUALIFIER.ERA] =
    P(StringIn("a.d.", "ad", "b.c.", "bc").! map QUALIFIER.ERA)
  def gaps[_: P]: P[QUALIFIER.GAPS.type] = keyword(QUALIFIER.GAPS, "[gaps]")

  def token[_: P]: P[QualifierToken] = P(
    pre | post | mid | early | late | about | approx | between | circa | floruit | era | gaps
  )
}

object PeriodLexer extends Parser[Seq[PeriodToken]] {
  def parser[_: P]: P[Seq[PeriodToken]] =
    Start ~ (ElementLexer.token | QualifierLexer.token).rep(1) ~ End
}
