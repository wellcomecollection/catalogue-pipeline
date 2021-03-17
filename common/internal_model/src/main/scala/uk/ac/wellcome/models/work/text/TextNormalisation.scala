package uk.ac.wellcome.models.work.text

import scala.util.matching.Regex

object TextNormalisation {
  implicit class TextNormalisationOps(s: String) {
    /** Remove the given character and any surrounding whitespace */
    def trimTrailing(c: Char): String = {
      val regexp = """\s*[""" + Regex.quote(c.toString) + """]\s*$"""
      s.replaceAll(regexp, "")
    }

    def sentenceCase: String =
      s.capitalize
  }
}
