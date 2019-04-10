package uk.ac.wellcome.platform.transformer.sierra.transformers.parsers

import java.lang.Character._

import org.apache.commons.lang3.StringUtils.leftPad

trait MiroIdParser {
  val miroIdPadToDigits = 7

  def parse089MiroId(miroId: String) = {
    val strippedMiroId: String = miroId.replace(" ", "")
    strippedMiroId.toList match {
      case (head: Char) :: (tail: List[Char])
        if isLetter(head) && tail.forall(isDigit) =>
        Some(formatMiroId(head, tail))
      case _ =>
        None
    }
  }

  private def formatMiroId(head: Char, tail: List[Char]) = {
    s"$head${leftPad(tail.mkString, miroIdPadToDigits, '0')}"
  }
}
