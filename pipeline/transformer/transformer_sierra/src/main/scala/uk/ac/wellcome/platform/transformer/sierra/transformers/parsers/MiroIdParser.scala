package uk.ac.wellcome.platform.transformer.sierra.transformers.parsers

import org.apache.commons.lang3.StringUtils.leftPad

import scala.util.matching.Regex

trait MiroIdParser {
  val miroIdPadToDigits = 7

  // Miro IDs start with a prefix letter, followed by a sequence of digits,
  // and optionally end in a suffix of letters
  lazy val miroIdRegex: Regex = "^([a-zA-Z])([0-9]+)([a-zA-Z]*)$".r

  def parse089MiroId(miroId: String): Option[String] = {
    val strippedMiroId = miroId.replace(" ", "")
    strippedMiroId match {
      case miroIdRegex(prefix, digits, suffix) =>
        Some(formatMiroId(prefix.charAt(0), digits, suffix))
      case _ => None
    }
  }

  private def formatMiroId(prefix: Char, digits: String, suffix: String) =
    s"$prefix${leftPad(digits, miroIdPadToDigits, '0')}$suffix"
}
