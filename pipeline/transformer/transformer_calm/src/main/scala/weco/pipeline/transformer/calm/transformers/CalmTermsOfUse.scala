package weco.pipeline.transformer.calm.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.locations.AccessStatus
import weco.catalogue.internal_model.work.{Note, NoteType}
import weco.catalogue.source_model.calm.CalmRecord
import weco.pipeline.transformer.calm.models.CalmRecordOps

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object CalmTermsOfUse extends CalmRecordOps with Logging {
  def apply(record: CalmRecord): List[Note] = {
    val accessConditions = getAccessConditions(record)
    val accessStatus = CalmAccessStatus(record)

    val closedUntil = record.get("ClosedUntil").map(parseAsDate)
    val restrictedUntil = record.get("UserDate1").map(parseAsDate)

    val terms =
      (accessConditions, accessStatus, closedUntil, restrictedUntil) match {

        // If there are conditions and no dates, we just create a sentence and
        // append the access status.
        //
        // Examples:
        //
        //      The papers are available subject to the usual conditions of access
        //      to Archives and Manuscripts material. Open.
        //
        //      Closed on depositor agreement.
        //
        case (Some(conditions), Some(status), None, None) =>
          Some(conditions)

        // If the item is closed and we have a ClosedUntil date, we create a message.
        // We don't repeat the access status/date if they're already included in the text.
        //
        // Examples:
        //
        //      Closed under the Data Protection Act until 1st January 2039.
        //
        //      This file is closed for business sensitivity reasons and cannot be accessed.
        //      Once the closure period expires the file will be re-reviewed and the closure may be extended.
        //      Closed until 1 January 2065.
        //
        case (Some(conditions), Some(AccessStatus.Closed), Some(closedUntil), _)
            if conditions.toLowerCase.contains("closed") & conditions
              .containsDate(closedUntil) =>
          Some(conditions)
        case (
              Some(conditions),
              Some(AccessStatus.Closed),
              Some(closedUntil),
              _
            ) =>
          Some(
            s"$conditions Closed until ${closedUntil.format(displayFormat)}."
          )
        case (None, Some(AccessStatus.Closed), Some(closedUntil), _) =>
          Some(s"Closed until ${closedUntil.format(displayFormat)}.")

        // Similarly, if an item is restricted and we have a RestrictedUntil date,
        // we create a message.
        case (
              Some(conditions),
              Some(AccessStatus.Restricted),
              _,
              Some(restrictedUntil)
            )
            if conditions.toLowerCase.contains("restricted") & conditions
              .containsDate(restrictedUntil) =>
          Some(conditions)
        case (
              Some(conditions),
              Some(AccessStatus.Restricted),
              _,
              Some(restrictedUntil)
            ) =>
          Some(
            s"$conditions Restricted until ${restrictedUntil.format(displayFormat)}."
          )
        case (None, Some(AccessStatus.Restricted), _, Some(restrictedUntil)) =>
          Some(s"Restricted until ${restrictedUntil.format(displayFormat)}.")

        // Some items require permission *and* they're restricted.
        //
        // Examples:
        //
        //      Permission must be obtained from the Winnicott Trust before access can be
        //      granted to this item. This item is also restricted. You will need to complete
        //      a Restricted Access form agreeing to anonymise personal data before you will
        //      be allowed to view this item. Please see our Access Policy for more details.
        //      Restricted until 1 January 2027.
        //
        case (
              Some(conditions),
              Some(AccessStatus.PermissionRequired),
              _,
              Some(restrictedUntil)
            )
            if conditions.toLowerCase.contains(
              "permission"
            ) & conditions.hasRestrictions & conditions.containsDate(
              restrictedUntil
            ) =>
          Some(conditions)
        case (
              Some(conditions),
              Some(AccessStatus.PermissionRequired),
              _,
              Some(restrictedUntil)
            )
            if conditions.toLowerCase.contains(
              "permission"
            ) & conditions.hasRestrictions =>
          Some(
            s"$conditions Restricted until ${restrictedUntil.format(displayFormat)}."
          )

        // If the item only has an access status or there's no information at all, there's
        // nothing useful to put in the access terms.
        case (None, _, None, None) =>
          None

        // Otherwise, we create a TermsOfUse note that smushes together all the bits of
        // access information that we have.  This isn't particularly nice, but it's what
        // Encore currently does, and in most cases we'll do a better job of it.
        //
        // This currently affects ~200 of 350k Calm items, and in some cases it reflects
        // a mistake in the underlying data that should be fixed.  This catch-all approach
        // will highlight issues, and then we can ask C&R to sort them out.
        case (conditions, status, closedUntil, restrictedUntil) =>
          warn(s"Unclear how to create a TermsOfUse note for item ${record.id}")
          val parts = Seq(
            conditions,
            restrictedUntil.map(d => s"Restricted until ${d.format(displayFormat)}."),
            closedUntil.map(d => s"Closed until ${d.format(displayFormat)}.")
          ).flatten

          if (parts.isEmpty) None else Some(parts.mkString(" "))
      }

    terms
      .map(contents => Note(contents = contents, noteType = NoteType.TermsOfUse))
      .toList
  }

  // e.g. 1 January 2021
  private val displayFormat = DateTimeFormatter.ofPattern("d MMMM yyyy")

  private def getAccessConditions(record: CalmRecord): Option[String] =
    record.getJoined("AccessConditions").map {
      s =>
        if (s.endsWith(".")) s else s + "."
    }

  // e.g. parsing dates "01/01/2039"
  private def parseAsDate(s: String): LocalDate =
    LocalDate.parse(s, DateTimeFormatter.ofPattern("d/M/yyyy"))

  implicit class StringOps(s: String) {
    def containsDate(d: LocalDate): Boolean = {
      // Remove any ordinals, e.g. "1st", "2nd"
      val normalisedS = s
        .replace("1st", "1")
        .replace("2nd", "2")
        .replace("3rd", "3")
        .replace("th", "")

      Seq(
        "d MMMM yyyy", // 1 January 2021
        "dd/MM/yyyy" // 01/01/2021
      ).exists {
        fmt =>
          val dateString = d.format(DateTimeFormatter.ofPattern(fmt))
          normalisedS.contains(s"until $dateString")
      }
    }

    def hasRestrictions: Boolean =
      Seq("restricted", "restrictions").exists(
        s.toLowerCase.contains(_)
      )
  }
}
