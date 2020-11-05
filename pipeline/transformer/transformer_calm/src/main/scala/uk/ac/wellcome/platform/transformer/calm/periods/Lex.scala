package uk.ac.wellcome.platform.transformer.calm.periods

import fastparse._
import NoWhitespace._
import uk.ac.wellcome.models.parse.ParserUtils

import scala.Function.const

object Lex extends ParserUtils {
  def keyword[T, _: P](token: T, str: String): P[T] =
    LiteralStr(str) map const(token)
  def keywords[T, _: P](token: T, p: P[_]): P[T] =
    p map const(token)
  def ordinalSuffix[_: P]: P[Unit] = StringIn("th", "rd", "st", "nd")
  def int[_: P]: P[Int] =
    P(CharIn("0-9").rep(1).!.map(_.toInt))

  // Elements
  def numericCentury[_: P]: P[Int] =
    P(int ~ "s" filter (_ % 100 == 0) map (_ / 100))
  def textCentury[_: P]: P[Int] =
    P(int ~ ordinalSuffix.? ~ ws ~ StringIn("century", "cent.", "cent") map (_ - 1))
  def century[_: P]: P[Int] = P(numericCentury | textCentury)

  def decade[_: P]: P[Int] =
    P(int ~ "s" filter (_ % 10 == 0))

  def season[_: P]: P[String] =
    P(StringIn("spring", "summer", "autumn", "fall", "winter").! map {
      case "fall" => "autumn"
      case season => season
    })

  def lawTerm[_: P]: P[String] =
    P(StringIn("michaelmas", "hilary", "easter", "trinity").!)

  def noDate[_: P]: P[Unit] = StringIn("n.d.", "undated", "unknown")

  def present[_: P]: P[Unit] = "present"

  // Qualifiers
  def pre[_: P]: P[Qualifier.Pre.type] = keyword(Qualifier.Pre, "pre")
  def post[_: P]: P[Qualifier.Post.type] = keyword(Qualifier.Post, "post")
  def mid[_: P]: P[Qualifier.Mid.type] =
    keywords(Qualifier.Mid, StringIn("middle", "mid.", "mid"))
  def early[_: P]: P[Qualifier.Early.type] = keyword(Qualifier.Early, "early")
  def late[_: P]: P[Qualifier.Late.type] = keyword(Qualifier.Late, "late")
  def about[_: P]: P[Qualifier.About.type] = keyword(Qualifier.About, "about")
  def approx[_: P]: P[Qualifier.Approx.type] =
    keyword(Qualifier.Approx, "approx")
  def between[_: P]: P[Qualifier.Between.type] =
    keyword(Qualifier.Between, "between")
  def circa[_: P]: P[Qualifier.Circa.type] =
    keywords(Qualifier.Circa, StringIn("circa", "circ.", "circ", "c.", "c"))
  def floruit[_: P]: P[Qualifier.Floruit.type] =
    keywords(Qualifier.Floruit, StringIn("floruit", "fl.", "fl"))
  def era[_: P]: P[Qualifier.Era] =
    P(StringIn("a.d.", "ad", "b.c.", "bc").! map {
      case "a.d." | "ad" => Qualifier.Era("ad")
      case "b.c." | "bc" => Qualifier.Era("bc")
    })
  def gaps[_: P]: P[Qualifier.Gaps.type] = keyword(Qualifier.Gaps, "[gaps]")

  def qualifier[_: P]: P[Qualifier] = P(
    pre | post | mid | early | late | about | approx | between | circa | floruit | era | gaps
  )
}
