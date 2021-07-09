package weco.pipeline.transformer.identifiers

import scala.util.matching.Regex

object IdentifierRegexes {
  /*
   * A Sierra system number (aka a bnumber) is composed of:
   * - A prefix 'b'
   * - A 7 digit identifier
   * - A check character, which can be any digit or an 'x'
   */
  lazy val sierraSystemNumber: Regex = "^b[0-9]{7}[0-9x]$".r

  /*
   * A Sierra identifier is 7 digits
   * (the same as the middle portion of a system number)
   */
  lazy val sierraIdentifier: Regex = "^[0-9]{7}$".r

  /* Regex for parsing a Miro ID.  Examples of valid IDs, all taken from
   * the Sierra data:
   *
   *    L0046161, V0033167F1, V0032544ECL, V0009118ETLL, L0025088F01
   *
   * Regarding suffixes (from a slack discussion with collections):
   *
   * You should find two types of suffix, one beginning with E and one beginning with F.
   * The E suffix was used to introduce a further character that distinguished two or
   * more works that were photographed on the same piece of film
   * (colour transparency and black&white negative).
   *
   *   L = Left
   *   R = Right.
   *   T=Top
   *   B=Bottom
   *   TR =Top Right
   *   etc.
   *
   * To add to that, there are also M series images with suffix EA, EB, EC, ED, EE, and EF.
   * They don't seem to mean anything (like L = left), they seem to just be running letters
   * to slip the image in line.
   */
  lazy val miroImageNumber: Regex =
    "^[A-Z][0-9]{7}[A-Z]{0,4}[0-9]{0,2}$".r

  /*
   * Iconographic numbers are a series of digits followed by an 'i'
   */
  lazy val iconographicNumber: Regex = "^[0-9]+i$".r

  /*
   * Capture any string starting with `dig` followed by a non-zero number
   * of alphabet characters.  The digcode is only useful if it identifies
   * a digitisation project, hence requiring a non-empty suffix.
   */
  lazy val wellcomeDigcode: Regex = "^dig[a-z]+$".r

  /*
   * A RefNo is composed of alphanumeric identifiers separated by slashes
   */
  lazy val calmRefNo: Regex = "^([A-Za-z0-9]+/?)+$".r
}
