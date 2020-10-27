package uk.ac.wellcome.platform.transformer.sierra.transformers.parsers

import org.apache.commons.lang3.StringUtils.leftPad

import scala.util.matching.Regex

object MiroIdParsing {
  // Regex for parsing a Miro ID.  Examples of valid IDs, all taken from
  // the Sierra data:
  //
  //    L0046161, V0033167F1, V0032544ECL
  //
  private val fullMiroId: Regex = "[A-Z][0-9]{7}[A-Z]{0,3}[0-9]?".r
  // groups are (prefix, digits, suffix)
  private val miroIdComponents: Regex =
    "([A-Z])([0-9]{1,7})([A-Z]{0,3}[0-9]?)".r

  private object URLRegex {
    // Examples:
    //
    //    http://wellcomeimages.org/indexplus/image/L0046161.html
    //    http://wellcomeimages.org/indexplus/image/L0041574.html.html
    //
    val indexPlus: Regex =
      s"^http://wellcomeimages\\.org/indexplus/image/($fullMiroId)(?:\\.html){0,2}$$".r

    // Examples:
    //
    //    http://wellcomeimages.org/ixbin/hixclient?MIROPAC=L0076330
    //    http://wellcomeimages.org/ixbin/hixclient?MIROPAC=V0000492EB
    //    http://wellcomeimages.org/ixbin/hixclient?MIROPAC=V0031553F1
    //
    val hixClient: Regex =
      s"^http://wellcomeimages\\.org/ixbin/hixclient\\?MIROPAC=($fullMiroId)$$".r

    // Examples:
    //
    //    http://wellcomeimages.org/ixbinixclient.exe?MIROPAC=V0010851.html.html
    //
    val ixbinixClient: Regex =
      s"^http://wellcomeimages\\.org/ixbinixclient\\.exe\\?MIROPAC=($fullMiroId)\\.html\\.html$$".r

    // Examples:
    //
    //    http://wellcomeimages.org/ixbinixclient.exe?image=M0009946.html
    //
    val altIxbinixClient: Regex =
      s"^http://wellcomeimages\\.org/ixbinixclient\\.exe\\?image=($fullMiroId)\\.html$$".r
  }

  def maybeFromURL(url: String): Option[String] = url match {
    case URLRegex.indexPlus(miroID)        => Some(miroID)
    case URLRegex.hixClient(miroID)        => Some(miroID)
    case URLRegex.ixbinixClient(miroID)    => Some(miroID)
    case URLRegex.altIxbinixClient(miroID) => Some(miroID)
    case _                                 => None
  }

  def maybeFromString(str: String): Option[String] = {
    // Strip whitespace because IDs in the 089 field usually
    // look something like "V 123"
    val strippedMiroId = str.replace(" ", "")
    strippedMiroId match {
      case miroIdComponents(prefix, digits, suffix) =>
        Some(formatMiroId(prefix, digits, suffix))
      case _ => None
    }
  }

  def stripSuffix(id: String): String = id match {
    case miroIdComponents(prefix, digits, _) => prefix + digits
  }

  private def formatMiroId(prefix: String, digits: String, suffix: String) =
    s"$prefix${leftPad(digits, 7, '0')}$suffix"
}
