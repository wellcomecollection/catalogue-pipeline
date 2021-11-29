package weco.pipeline.transformer.sierra.transformers

import grizzled.slf4j.Logging

import java.net.URL
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraBibData
import weco.sierra.models.identifiers.SierraBibNumber
import weco.sierra.models.marc.{Subfield, VarField}

import scala.util.Try

object SierraDescription
    extends SierraIdentifiedDataTransformer
    with SierraQueryOps
    with Logging {

  type Output = Option[String]

  // Populate wwork:description.
  //
  // We use MARC field "520".  Rules:
  //
  //  - Join 520 ǂa, ǂb and ǂu with a space
  //  - If the ǂu looks like a URL, we wrap it in <a> tags with the URL as the
  //    link text
  //  - Wrap resulting string in <p> tags
  //  - Join each occurrence of 520 into description
  //
  // Notes:
  //  - Both ǂa (summary) and ǂb (expansion of summary note) are
  //    non-repeatable subfields.  ǂu (Uniform Resource Identifier)
  //    is repeatable.
  //  - We never expect to see a record with $b but not $a.
  //
  // https://www.loc.gov/marc/bibliographic/bd520.html
  //
  def apply(bibId: SierraBibNumber, bibData: SierraBibData): Option[String] = {
    val description = bibData
      .varfieldsWithTag("520")
      .map { descriptionFromVarfield(bibId, _) }
      .mkString("\n")

    if (description.nonEmpty) Some(description) else None
  }

  private def descriptionFromVarfield(bibId: SierraBibNumber,
                                      vf: VarField): String = {
    val subfields =
      Seq(
        vf.nonrepeatableSubfieldWithTag(tag = "a"),
        vf.nonrepeatableSubfieldWithTag(tag = "b")
      ).flatten ++ vf.subfieldsWithTag("u")

    val contents =
      subfields
        .map {
          case Subfield("u", contents) if isUrl(contents) =>
            s"""<a href="$contents">$contents</a>"""

          // The spec says that MARC 520 ǂu is "Uniform Resource Identifier", which
          // isn't the same as being a URL.  We don't want to make non-URL text
          // clickable; we're also not sure what the data that isn't a URL looks like.
          //
          // For now, log the value and don't make it clickable -- we can decide how
          // best to handle it later.
          case Subfield("u", contents) =>
            warn(
              s"${bibId.withCheckDigit} has MARC 520 ǂu which doesn't look like a URL: $contents")
            contents

          case Subfield(_, contents) => contents
        }
        .mkString(" ")

    s"<p>$contents</p>"
  }

  private def isUrl(s: String): Boolean =
    Try { new URL(s) }.isSuccess
}
