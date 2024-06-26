package weco.pipeline.transformer.marc_common.transformers
import grizzled.slf4j.Logging
import weco.pipeline.transformer.exceptions.ShouldNotTransformException
import weco.pipeline.transformer.marc_common.logging.LoggingContext
import weco.pipeline.transformer.marc_common.models.{
  MarcField,
  MarcFieldOps,
  MarcRecord
}

import java.net.URL
import scala.util.{Failure, Success, Try}

// Populate wwork:description.
//
// We use MARC field "520".  Rules:
//
//  - Join 520 ǂa, ǂb, ǂc and ǂu with a space
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
object MarcDescription
    extends MarcDataTransformerWithLoggingContext
    with MarcFieldOps
    with Logging {

  override type Output = Option[String]

  override def apply(record: MarcRecord)(
    implicit ctx: LoggingContext
  ): Option[String] = {
    val description =
      record.fieldsWithTags("520").map(descriptionFromField).mkString("\n")
    if (description.nonEmpty) Some(description) else None
  }

  private def descriptionFromField(field: MarcField)(
    implicit ctx: LoggingContext
  ): String = {
    Seq("a", "b", "c")
      .flatMap {
        tag: String =>
          field.onlySubfieldWith(tag) match {
            case Failure(exception) =>
              throw new ShouldNotTransformException(exception.getMessage)
            case Success(subfield) => subfield
          }
      }
      .map(_.content.trim) ++ field.subfields
      .filter(_.tag == "u")
      .map(subfield => makeLink(subfield.content.trim)) match {
      case Nil     => ""
      case strings => s"<p>${strings.mkString(" ")}</p>"
    }
  }

  private def makeLink(maybeURL: String)(
    implicit ctx: LoggingContext
  ): String =
    if (isUrl(maybeURL))
      s"""<a href="$maybeURL">$maybeURL</a>"""
    else {
      // The spec says that MARC 520 ǂu is "Uniform Resource Identifier", which
      // isn't the same as being a URL.  We don't want to make non-URL text
      // clickable; we're also not sure what the data that isn't a URL looks like.
      //
      // For now, log the value and don't make it clickable -- we can decide how
      // best to handle it later.
      warn(ctx(s"has MARC 520 ǂu which doesn't look like a URL: $maybeURL"))
      maybeURL
    }

  private def isUrl(s: String): Boolean =
    Try { new URL(s) }.isSuccess
}
