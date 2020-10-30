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
  def numericCentury[_: P]: P[Century] =
    P(int ~ "s" filter (_ % 100 == 0) map { year =>
      Century((year / 100) + 1)
    })
  def textCentury[_: P]: P[Century] =
    P(int ~ ordinalSuffix.? ~ StringIn("century", "cent.", "cent") map Century)
  def century[_: P]: P[Century] = P(numericCentury | textCentury)
  def decade[_: P]: P[Decade] = P(int ~ "s" filter (_ % 10 == 0) map Decade)
  def month[_: P]: P[Month] =
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
        Month(month.slice(0, 3))
      })
  def year[_: P]: P[Year] =
    P(CharIn("0-9").repX(exactly = 4).! map (_.toInt) map Year)
  def ordinal[_: P]: P[Ordinal] =
    P(int ~ ordinalSuffix map Ordinal)
  def number[_: P]: P[Number] = P(int map Number)
  def season[_: P]: P[Season] =
    P(StringIn("spring", "summer", "autumn", "fall", "winter").! map Season)
  def lawTerm[_: P]: P[LawTerm] =
    P(StringIn("michaelmas", "hilary", "easter", "trinity").! map LawTerm)
  def noDate[_: P]: P[NoDate.type] =
    keywords(NoDate, StringIn("n.d.", "undated", "unknown"))
  def present[_: P]: P[Present.type] = keyword(Present, "present")
  def slash[_: P]: P[Slash.type] = keyword(Slash, "/")
  def rangeSeparator[_: P]: P[RangeSeparator.type] =
    keywords(RangeSeparator, StringIn("between", "to", "x", "-"))

  def token[_: P]: P[ElementToken] =
    P(
      century | decade | ordinal | month | year | number | slash | season | lawTerm | noDate | present | rangeSeparator
    )
}

private object QualifierLexer extends Helpers {
  def pre[_: P]: P[Pre.type] = keyword(Pre, "pre")
  def post[_: P]: P[Post.type] = keyword(Post, "post")
  def mid[_: P]: P[Mid.type] =
    keywords(Mid, StringIn("middle", "mid.", "mid"))
  def early[_: P]: P[Early.type] = keyword(Early, "early")
  def late[_: P]: P[Late.type] = keyword(Late, "late")
  def about[_: P]: P[About.type] = keyword(About, "about")
  def approx[_: P]: P[Approx.type] =
    keyword(Approx, "approx")
  def between[_: P]: P[Between.type] =
    keyword(Between, "between")
  def circa[_: P]: P[Circa.type] =
    keywords(Circa, StringIn("circa", "circ.", "circ", "c.", "c"))
  def floruit[_: P]: P[Floruit.type] =
    keywords(Floruit, StringIn("floruit", "fl.", "fl"))
  def era[_: P]: P[Era] =
    P(StringIn("a.d.", "ad", "b.c.", "bc").! map Era)
  def gaps[_: P]: P[Gaps.type] = keyword(Gaps, "[gaps]")

  def token[_: P]: P[QualifierToken] = P(
    pre | post | mid | early | late | about | approx | between | circa | floruit | era | gaps
  )
}

object PeriodLexer extends Parser[Seq[PeriodToken]] {
  def parser[_: P]: P[Seq[PeriodToken]] =
    Start ~ (ElementLexer.token | QualifierLexer.token).rep(1) ~ End
}
