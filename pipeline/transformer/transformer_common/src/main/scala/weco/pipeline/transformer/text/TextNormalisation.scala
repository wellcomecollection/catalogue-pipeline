package weco.pipeline.transformer.text

import scala.util.matching.Regex

object TextNormalisation {
  implicit class TextNormalisationOps(s: String) {

    /** Remove the given character and any surrounding whitespace */
    def trimTrailing(c: Char): String = {
      val regexp = """\s*[""" + Regex.quote(c.toString) + """]\s*$"""
      s.replaceAll(regexp, "")
    }

    /** Remove a single trailing period, but not ellipses */
    def trimTrailingPeriod: String =
      s.replaceAll("""([^\.])\.\s*$""", "$1")
        .replaceAll("""\s*$""", "")

    /** Is this string just whitespace?
      *
      * Note that this includes non-breaking spaces as whitespace, which aren't removed by .trim().
      */
    def isWhitespace: Boolean =
      s.replace('\u00a0', ' ').trim.isEmpty

    def sentenceCase: String =
      s.capitalize
  }
}
