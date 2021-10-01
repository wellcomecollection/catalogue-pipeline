package weco.pipeline.transformer.calm.models

import weco.catalogue.source_model.calm.CalmRecord
import weco.pipeline.transformer.text.TextNormalisation._

trait CalmRecordOps {

  implicit class CalmRecordOps(record: CalmRecord) {
    def get(key: String): Option[String] =
      getList(key).distinct.headOption

    def getList(key: String): List[String] =
      record.data
        .getOrElse(key, Nil)
        .filterNot(_.isWhitespace)
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(fixEncoding)

    def getJoined(key: String, separator: String = " "): Option[String] =
      getList(key) match {
        case Nil   => None
        case items => Some(items.mkString(separator))
      }
  }

  // A handful of Calm records have values in some unknown encoding.
  // Turn them into the same encoding as the rest of the text.
  //
  // We know that \u0093 and \u0094 are opening/closing double curly quotes --
  // see discussion in https://wellcome.slack.com/archives/C8X9YKM5X/p1626790241136100
  //
  // Then I looked through the Calm records to see what similar characters
  // we had that might be encoded correctly. Based on this blog post and
  // reference table, I suspect it's some flavour of ISO-8859-1:
  // https://javaevangelist.blogspot.com/2012/07/iso-8859-1-character-encodings.html
  //
  // I ran the Calm records through a Python library ftfy ("fixes text for you"),
  // which is designed to detect and fix this sort of mojibake.
  //
  // This is a hard-coded list of replacements rather than a full-on encoding
  // fixer because:
  //
  //    1) I don't want to risk introducing more problems than I solve.
  //    2) I don't fully understand what encodings have been mangled here -- these
  //       strings have already been through multiple steps, both in the transformer
  //       and the adapter, and it's not important enough to do a more detailed analysis.
  //
  private def fixEncoding(s: String): String =
    s.replaceAll("â\u0080\u0093", "–")
      .replaceAll("Â°", "°")
      .replaceAll("Ã§", "ç")
      .replaceAll("Ã¨", "è")
      .replaceAll("Ã\u0089", "É")
      .replaceAll("Ãª", "ê")
      .replaceAll("\u0080", "€")
      .replaceAll("\u0082", "‚")
      .replaceAll("\u0085", "…")
      .replaceAll("\u0086", "†")
      .replaceAll("\u008a", "Š")
      .replaceAll("\u008c", "Œ")
      .replaceAll("\u0091", "‘")
      .replaceAll("\u0092", "’")
      .replaceAll("\u0093", "“")
      .replaceAll("\u0094", "”")
      .replaceAll("\u0095", "•")
      .replaceAll("\u0096", "–")
      .replaceAll("\u0097", "—")
      .replaceAll("\u0099", "™")
      .replaceAll("\u009a", "š")
      .replaceAll("\u009b", "›")
      .replaceAll("\u009c", "œ")
      .replaceAll("\u009e", "ž")
}
