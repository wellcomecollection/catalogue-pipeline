package weco.pipeline.transformer.sierra.transformers

import grizzled.slf4j.Logging
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.identifiers.TypedSierraRecordNumber
import weco.sierra.models.marc.{Subfield, VarField}

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
//
// Currently this class only handles 853/863 pairs (which describe the holdings
// themselves); there are also 854/864 (indexes) and 855/865 (supplements).
// We don't present 854/864 or 855/865 in the Catalogue API, but this class is
// written in a generic way that would allow supporting them if we wanted to later.
//
object SierraHoldingsEnumeration extends SierraQueryOps with Logging {

  def apply(id: TypedSierraRecordNumber,
            varFields: List[VarField]): List[String] =
    getHumanWrittenEnumeration(varFields) ++
      getAutomaticallyPopulatedEnumeration(id, varFields)

  // Field tag h is used by librarians to describe the contents of our holdings
  // when they wrote the description themselves.  Note that 853/863 will also
  // have field tag h, so we want to filter to cases where it's just an h and
  // a textual description.
  //
  // See https://documentation.iii.com/sierrahelp/Content/sril/sril_records_varfld_types_holdings.html
  //
  private def getHumanWrittenEnumeration(
    varFields: List[VarField]): List[String] =
    varFields
      .filter(vf => vf.fieldTag.contains("h"))
      .filterNot(vf => vf.marcTag.isDefined)
      .flatMap(vf => vf.content)

  val labelTag = "853"
  val valueTag = "863"

  private def getAutomaticallyPopulatedEnumeration(
    id: TypedSierraRecordNumber,
    varFields: List[VarField]): List[String] = {

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

    val labelsLookup = labels.map {
      case label @ Label(link, _) => link -> label
    }.toMap

    // We have seen records where two instances of field 853 have the
    // same sequence number, but only when the whole field is duplicated.
    // That's not an issue -- they'd generate the same caption regardless.
    //
    // It's worth investigating if we have *different* data in the fields,
    // because then we might have an unstable caption.
    if (labelsLookup.size != labels.distinct.size) {
      warn(
        s"${id.withoutCheckDigit}: multiple instances of $labelTag with the same sequence number")
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
            warn(
              s"${id.withCheckDigit}: an instance of $valueTag refers to a missing sequence number in $labelTag: ${value.varField}")
            None
        }
      }
      .sortBy { case (_, value) => (value.link, value.sequence) }
      .map {
        case (label, value) =>
          // We concatenate the contents of the public note in subfield ǂz.
          // This is completely separate from the logic for combining the
          // labels/values from the 85X/86X pair.
          val publicNote =
            value.varField.subfieldsWithTag("z").map { _.content }.mkString(" ")

          createString(id, label, value) + " " + publicNote
      }
      .map { _.trim }
      .distinct
  }

  private def createString(id: TypedSierraRecordNumber,
                           label: Label,
                           value: Value): String = {
    val parts: Seq[(String, String)] =
      value.varField.subfields
        .filterNot {
          // This is the link field (see above), so we don't need to include it when
          // creating the display string.
          _.tag == "8"
        }
        .flatMap { sf =>
          label.varField.subfieldsWithTag(sf.tag).headOption match {
            case Some(Subfield(_, subfieldLabel)) =>
              Some((subfieldLabel, sf.content))
            case None => None
          }
        }
        .filterNot { case (_, value) => value.trim == "-" }

    // We fork here to make sure we handle ranges, e.g.
    //
    //    853 v.|bno.
    //    863 8-69|b-1
    //    863 69|b2-3
    //
    // We want to expand these to "v.8 - v.69:no.1" and "v.69:no.2 - v.69:no.3".
    //
    // A couple of rules I've inferred from how the old Wellcome Library site works:
    //
    //  * if some parts of the value are a range but others aren't (e.g. "69" and "2-3" above),
    //    then you use the single value in the unranged part as both start/end.
    //    Hence: `.head` and `.last`.
    //
    //  * It is totally possible to have a three-part range, e.g. "01-02-03".
    //    The Library site handles this by dropping all but the first two parts, i.e. treating
    //    this as synonymous with "01-02".
    //
    // The tests have more examples for all the cases this range logic is meant to handle.
    //
    if (parts.exists { case (_, value) => value.contains("-") }) {
      val startParts = parts.map {
        case (label, value) => (label, value.split("-", 2).head)
      }
      val endParts = parts.map {
        case (label, value) => (label, value.split("-", 2).last)
      }

      val startString = concatenateParts(id, startParts)
      val endString = concatenateParts(id, endParts)

      // It's possible the start/end of the range may be the same.  If so, collapse
      // them into a single value to make them easier to read.
      if (startString == endString) {
        startString
      } else {
        s"$startString - $endString"
      }
    } else {
      concatenateParts(id, parts)
    }
  }

  private def concatenateParts(id: TypedSierraRecordNumber,
                               parts: Seq[(String, String)]): String = {
    val nonEmptyParts = parts.filterNot { case (_, value) => value.isEmpty }

    // We split the label/values into date-based and textual.  The dates in
    // the MARC are numeric and need to be rendered in a nicer format;
    // the text values can be presented as is.
    val dateParts =
      nonEmptyParts
        .filter {
          case (label, _) =>
            label.toLowerCase.hasSubstring("season", "year", "month", "day")
        }

    val textualParts = nonEmptyParts.filterNot { dateParts.contains }

    val datePartsMap =
      dateParts
        .map { case (label, value) => label.toLowerCase.stripParens -> value }
        .map {
          // If we have a range of months, we just take the first month of the range.
          // We might revisit this decision later; this was chosen to mimic the output
          // of the old Wellcome Library site.
          case (label, value) if label == "month" && value.contains("-") =>
            (label, value.split("-").head)

          case (label, value) => (label, value)
        }
        .toMap

    // Construct the date string.  We wrap this all in a Try block, and if something
    // goes wrong, just drop a warning log.  My hope is that errors be sufficiently
    // infrequent that we don't need to write separate paths for every different
    // way this could go wrong.
    val dateString = Try {
      val year = datePartsMap.get("year").map { _.stripSuffix(".") }

      val dateDisplayStrings =
        // e.g. "Spring 2000"
        if (datePartsMap.contains("season")) {
          List(
            datePartsMap
              .get("season")
              .flatMap { s =>
                toNamedMonth(id, Some(s))
              }
              .map(_.value),
            year
          )
        } else {
          toNamedMonth(id, datePartsMap.get("month")) match {
            // e.g. "1 Jul 2000"
            case Some(NamedMonthResult(value, isAllMonths)) if isAllMonths =>
              List(
                datePartsMap.get("day").map { _.stripPrefix("0") },
                Some(value),
                year
              )

            // e.g. "Summer 2000"
            case Some(NamedMonthResult(value, _)) =>
              List(
                Some(value),
                year
              )

            // e.g. "13 Sept. 2004"
            case _ =>
              List(
                datePartsMap.get("day").map { _.stripPrefix("0") },
                datePartsMap.get("month").flatMap { monthNames.get },
                year
              )
          }
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
        .foldRight("") {
          case (nextPart, accum) =>
            // I haven't worked out the exact rules around this yet.
            // In some cases, the old Wellcome Library site would join parts with
            // a space.  In others (e.g. "v.130:no.3"), it uses a colon.
            if (accum.startsWith("no.") && nextPart.startsWith("v")) {
              nextPart + ":" + accum
            } else {
              nextPart + " " + accum
            }
        }
        .trim

    (textualString, dateString) match {
      case (ts, Some(ds)) if ts.nonEmpty && ds.nonEmpty => s"$ts ($ds)"
      case (_, Some(ds)) if ds.nonEmpty                 => ds
      case (ts, _)                                      => ts
    }
  }

  // Take a numeric month value and turn it into a human-readable string.
  //
  // Examples:
  //
  //    03    -> Mar.
  //    21/22 -> Spring/Summer
  //
  private case class NamedMonthResult(value: String, isAllMonths: Boolean)

  private def toNamedMonth(id: TypedSierraRecordNumber,
                           maybeS: Option[String]): Option[NamedMonthResult] =
    maybeS.flatMap { s =>
      val parts = s.split("/").toList
      if (parts.forall(monthNames.contains)) {
        Some(
          NamedMonthResult(
            value = parts.map { monthNames(_) }.mkString("/"),
            isAllMonths = !parts.exists(seasonNames.contains)
          )
        )
      } else {
        warn(s"$id: Unable to completely parse ($s) as a named month.season")
        None
      }
    }

  private val seasonNames = Map(
    // Seasons are represented as two-digit numeric codes.
    // See https://help.oclc.org/Metadata_Services/Local_Holdings_Maintenance/OCLC_MARC_local_holdings_format_and_standards/8xx_fields/853_Captions_and_Pattern-Basic_Bibliographic_Unit
    "21" -> "Spring",
    "22" -> "Summer",
    "23" -> "Autumn",
    "24" -> "Winter"
  )

  private val monthNames = Map(
    "01" -> "Jan.",
    "02" -> "Feb.",
    "03" -> "Mar.",
    "04" -> "Apr.",
    "05" -> "May",
    "06" -> "June",
    "07" -> "July",
    "08" -> "Aug.",
    "09" -> "Sept.",
    "10" -> "Oct.",
    "11" -> "Nov.",
    "12" -> "Dec."
  ) ++ seasonNames

  /** Given an 85X varField from Sierra, try to create a Label.
    *
    * A Label contains the original varField and the link number.
    */
  private def createLabel(id: TypedSierraRecordNumber,
                          vf: VarField): Option[Label] =
    vf.subfieldsWithTag("8").headOption match {
      case Some(Subfield(_, content)) =>
        Try { content.toInt } match {
          case Success(link) => Some(Label(link, vf))
          case Failure(_) =>
            warn(
              s"${id.withCheckDigit}: an instance of $labelTag subfield ǂ8 has a non-numeric value: $content")
            None
        }

      case None =>
        warn(
          s"${id.withCheckDigit}: an instance of $labelTag is missing subfield ǂ8")
        None
    }

  /** Given an 86X varField from Sierra, try to create a Value.
    *
    * A Value contains the original varField, the link number and the sequence number.
    */
  private def createValue(id: TypedSierraRecordNumber,
                          vf: VarField): Option[Value] =
    vf.subfieldsWithTag("8").headOption match {
      case Some(Subfield(_, content)) =>
        Try { content.split('.').map(_.toInt).toSeq } match {
          case Success(Seq(link, sequence)) => Some(Value(link, sequence, vf))
          case _ =>
            warn(
              s"${id.withCheckDigit}: an instance of $valueTag subfield ǂ8 could not be parsed as a link/sequence: $content")
            None
        }

      case None =>
        warn(
          s"${id.withCheckDigit}: an instance of $valueTag is missing subfield ǂ8")
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
