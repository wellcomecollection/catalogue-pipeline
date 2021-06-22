package uk.ac.wellcome.platform.transformer.calm.transformers

import uk.ac.wellcome.platform.transformer.calm.CalmRecordOps
import weco.catalogue.internal_model.locations.AccessStatus
import weco.catalogue.internal_model.work.TermsOfUse
import weco.catalogue.source_model.calm.CalmRecord

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object CalmTermsOfUse extends CalmRecordOps {
  def apply(record: CalmRecord): Option[TermsOfUse] = {
    val accessConditions = getAccessConditions(record)
    val accessStatus = getAccessStatus(record)

    val closedUntil = record.get("ClosedUntil").map(parseAsDate)
    val restrictedUntil = record.get("RestrictedUntil")

    val terms =
      (accessConditions, accessStatus, closedUntil, restrictedUntil) match {

        // If there are conditions and no dates, we just create a sentence and
        // append the access status.  We don't repeat the access status if it's
        // already stated in the conditions.
        //
        // Examples:
        //
        //      The papers are available subject to the usual conditions of access
        //      to Archives and Manuscripts material. Open.
        //
        //      Closed on depositor agreement.
        //
        case (Some(conditions), Some(status), None, None) if conditions.startsWith(status.label) =>
          Some(conditions)
        case (Some(conditions), Some(status), None, None) =>
          Some(s"$conditions ${status.label}.")

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
            if conditions.isSingleSentence & conditions.toLowerCase.contains("closed") & conditions.containsDate(closedUntil) =>
          Some(conditions)
        case (Some(conditions), Some(AccessStatus.Closed), Some(closedUntil), _) =>
          Some(s"$conditions Closed until ${closedUntil.format(displayFormat)}.")

        case _ => throw new NotImplementedError(s"record = $record")
      }

    terms.map(TermsOfUse)
  }

  // e.g. 1 January 2021
  private val displayFormat = DateTimeFormatter.ofPattern("d MMMM yyyy")

  private def getAccessConditions(record: CalmRecord): Option[String] =
    record.getJoined("AccessConditions").map { s =>
      if (s.endsWith(".")) s else s + "."
    }

  private def getAccessStatus(record: CalmRecord): Option[AccessStatus] =
    record.get("AccessStatus") match {
      case Some("Open")       => Some(AccessStatus.Open)
      case Some("Closed")     => Some(AccessStatus.Closed)
      case Some("Restricted") | Some("Certain restrictions apply") => Some(AccessStatus.Restricted)
      case Some("By Appointment") => Some(AccessStatus.ByAppointment)
      case Some("Donor Permission") => Some(AccessStatus.PermissionRequired)
      case _                  => None
    }

  // e.g. parsing dates "01/01/2039"
  private def parseAsDate(s: String): LocalDate =
    LocalDate.parse(s, DateTimeFormatter.ofPattern("d/M/yyyy"))

  implicit class StringOps(s: String) {
    def isSingleSentence: Boolean =
      s.count(_ == '.') == 1

    def containsDate(d: LocalDate): Boolean = {
      // Remove any ordinals, e.g. "1st", "2nd"
      val normalisedS = s
        .replace("1st", "1")
        .replace("2nd", "2")
        .replace("3rd", "3")
        .replace("th", "")

      Seq("d MMMM yyyy").exists {
        fmt =>
          val dateString = d.format(DateTimeFormatter.ofPattern(fmt))
          normalisedS.contains(s"until $dateString")
      }
    }
  }
}
