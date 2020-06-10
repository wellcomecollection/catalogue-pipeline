package uk.ac.wellcome.platform.transformer.calm

import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist
import org.jsoup.nodes.Document.OutputSettings
import org.jsoup.parser.Parser

object NormaliseText {

  /* The whitelist allows the following text nodes (with appropriate
   * attributes):
   *
   * a, b, blockquote, br, cite, code, dd, dl, dt, em, i, li, ol, p, pre, q,
   * small, span, strike, strong, sub, sup, u, ul
   */
  private val whitelist = Whitelist.basic()

  private val settings = new OutputSettings().prettyPrint(false)

  def apply(str: String): String = {
    Jsoup
      .clean(str, "", whitelist, settings)
      .linesIterator
      .map(_.trimRight)
      .foldLeft(List.empty[String]) {
        // When html is stripped from a line and there is nothing else remaining
        // we are left with whitespace, so here we strip consecutive blank lines
        // to clean up the output.
        case (Nil, "")                => Nil
        case (previousStrs :+ "", "") => previousStrs :+ ""
        case (previousStrs, str)      => previousStrs :+ str
      }
      .map(Parser.unescapeEntities(_, false))
      .mkString("\n")
      .trim
  }

  implicit class StringOps(str: String) {
    def trimRight =
      str.replaceAll("\\s+$", "")
  }
}
