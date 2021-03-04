package uk.ac.wellcome.platform.transformer.sierra.transformers

import grizzled.slf4j.Logging
import uk.ac.wellcome.platform.transformer.sierra.source.{MarcSubfield, SierraQueryOps, VarField}
import weco.catalogue.sierra_adapter.models.TypedSierraRecordNumber

import scala.util.{Failure, Success, Try}

// The 85X/86X pairs are used to store structured captions -- the 85X contains
// the labels, the 856X contains the values.
//
// e.g. if you had the pair:
//
//    853 00 |810|avol.|i(year)
//    863 40 |810.1|a1|i1995
//
// then you combine the label/captions to get "vol.1 (1995)".
//
// The behaviour of this class is partly based on the published descriptions,
// partly on the observed behaviour on the old wellcomelibrary.org website.
object SierraHoldingsEnumeration extends SierraQueryOps with Logging {
  val labelTag = "853"
  val valueTag = "863"

  def apply(id: TypedSierraRecordNumber, varFields: List[VarField]): List[String] = {

    // The 85X and 86X pairs are associated based on the contents of subfield 8.
    //
    // The first number of subfield 8 is the link, which is a positive integer.
    // The second number is the sequence, also an integer, preceded by a decimal point.
    // e.g. "1.6" has link "1" and sequence "6".
    //
    // The 85X fields have a link value only; the 86X fields have a link and a sequence
    // value.  Go through and extract these values.
    val labels =
      varFields
        .filter { _.marcTag.contains(labelTag) }
        .flatMap { createLabel(id, _) }

    val values =
      varFields
        .filter { _.marcTag.contains(valueTag) }
        .flatMap { createValue(id, _) }

    val labelsLookup = labels
      .map { case label @ Label(link, _) => link -> label }
      .toMap

    // We have seen records where two instances of field 853 have the
    // same sequence number, but only when the whole field is duplicated.
    // That's not an issue -- they'd generate the same caption regardless.
    //
    // It's worth investigating if we have *different* data in the fields,
    // because then we might have an unstable caption.
    if (labelsLookup.size != labels.distinct.size) {
      warn(s"${id.withoutCheckDigit}: multiple instances of $labelTag with the same sequence number")
    }

    // We match the subfields on the label/value.
    //
    // For example, if we had the subfields:
    //
    //    853 00 |810|avol.|i(year)
    //    863 40 |810.1|a1|i1995
    //
    // Then the label in subfield ǂa is "vol." and the value is "1".
    //
    // If we can't find a label for a given value, we omit it.
    values
      .flatMap { value =>
        labelsLookup.get(value.link) match {
          case Some(label) => Some((label, value))
          case None =>
            warn(s"${id.withoutCheckDigit}: an instance of $valueTag refers to a missing sequence number in $labelTag: ${value.varField}")
            None
        }
      }
      .map { case (label, value) => createString(id, label, value) }
  }

  private def createString(id: TypedSierraRecordNumber, label: Label, value: Value): String = {
    val parts: Seq[(String, String)] =
      value
        .varField.subfields
        .filterNot {
          // This is the link field (see above), so we don't need to include it when
          // creating the display string.
          _.tag == "8"
        }
        .flatMap { sf =>
          label.varField.subfieldsWithTag(sf.tag).headOption match {
            case Some(MarcSubfield(_, subfieldLabel)) => Some((subfieldLabel, sf.content))
            case None => None
          }
        }
        .filterNot { case (_, value) => value.trim == "-" }

    if (parts.exists { case (_, value) => value.contains("-") }) {
      val startParts = parts.map { case (label, value) => (label, value.split("-", 2).head) }
      val endParts = parts.map { case (label, value) => (label, value.split("-", 2).last) }

      s"${concatenateParts(id, startParts)} - ${concatenateParts(id, endParts)}"
    } else {
      concatenateParts(id, parts)
    }
  }

  private def concatenateParts(id: TypedSierraRecordNumber, parts: Seq[(String, String)]): String = {
    val nonEmptyParts = parts.filterNot { case (_, value) => value.isEmpty }

    // We split the label/values into date-based and textual.  The dates in
    // the MARC are numeric and need to be rendered in a nicer format;
    // the text values can be presented as is.
    val dateParts =
      nonEmptyParts
        .filter { case (label, _) =>
          label.toLowerCase.hasSubstring("season", "year", "month", "day")
        }

    val textualParts = nonEmptyParts.filterNot { dateParts.contains }

    val datePartsMap =
      dateParts
        .map { case (label, value) => label.toLowerCase.stripParens -> value }
        .toMap

    // Construct the date string.  We wrap this all in a Try block, and if something
    // goes wrong, just drop a warning log.  My hope is that this will be sufficiently
    // infrequent that we don't need much sophisticated logic.
    val dateString = Try {
      val dateDisplayStrings =
        if (datePartsMap.contains("season")) {
          List(datePartsMap.get("season"), datePartsMap.get("year"))
        } else {
          List(
            datePartsMap.get("day"),
            datePartsMap.get("month"),
            datePartsMap.get("year"),
          )
        }

      dateDisplayStrings.flatten.mkString(" ")
    } match {
      case Success(s) => Some(s)
      case Failure(_) =>
        warn(s"${id.withCheckDigit}: unable to build a date from $valueTag")
        None
    }

    val textualString =
      textualParts
        .map {
          case (label, value) if label.startsWith("(") =>
            s"($value)"

          case (label, value) =>
            s"$label$value"
        }
        .foldRight("") { case (nextPart, accum) =>
          // I haven't worked out the exact rules around this yet.
          // In some cases, the old Wellcome Library site would join parts with
          // a space.  In others (e.g. "v.130:no.3"), it uses a colon.
          if (accum.startsWith("no.") && nextPart.startsWith("v.")) {
            nextPart + ":" + accum
          } else {
            nextPart + " " + accum
          }
        }
        .trim

    (textualString, dateString) match {
      case (ts, Some(ds)) if ts.nonEmpty && ds.nonEmpty => s"$ts ($ds)"
      case (_, Some(ds)) if ds.nonEmpty => ds
      case (ts, _) => ts
    }
  }

  /** Given an 85X varField from Sierra, try to create a Label.
    *
    * A Label contains the original varField and the link number.
    */
  private def createLabel(id: TypedSierraRecordNumber, vf: VarField): Option[Label] =
    vf.subfieldsWithTag("8").headOption match {
      case Some(MarcSubfield(_, content)) =>
        Try { content.toInt } match {
          case Success(link) => Some(Label(link, vf))
          case Failure(_) =>
            warn(s"${id.withoutCheckDigit}: an instance of $labelTag subfield ǂ8 has a non-numeric value: $content")
            None
        }

      case None =>
        warn(s"${id.withoutCheckDigit}: an instance of $labelTag is missing subfield ǂ8")
        None
    }

  /** Given an 86X varField from Sierra, try to create a Value.
   *
   * A Value contains the original varField, the link number and the sequence number.
   */
  private def createValue(id: TypedSierraRecordNumber, vf: VarField): Option[Value] =
    vf.subfieldsWithTag("8").headOption match {
      case Some(MarcSubfield(_, content)) =>
        Try { content.split('.').map(_.toInt).toSeq } match {
          case Success(Seq(link, sequence)) => Some(Value(link, sequence, vf))
          case _ =>
            warn(s"${id.withoutCheckDigit}: an instance of $labelTag subfield ǂ8 could not be parsed as a link/sequence: $content")
            None
        }

      case None =>
        warn(s"${id.withoutCheckDigit}: an instance of $labelTag is missing subfield ǂ8")
        None
    }

  private case class Label(link: Int, varField: VarField)
  private case class Value(link: Int, sequence: Int, varField: VarField)

  implicit private class StringOps(s: String) {
    def hasSubstring(substrings: String*): Boolean =
      substrings.exists { s.contains }

    def stripParens: String =
      s.stripPrefix("(").stripSuffix(")")
  }
}
