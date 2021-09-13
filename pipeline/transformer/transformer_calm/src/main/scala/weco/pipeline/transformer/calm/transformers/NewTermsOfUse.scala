package weco.pipeline.transformer.calm.transformers

import weco.catalogue.internal_model.locations.AccessStatus
import weco.catalogue.internal_model.work.TermsOfUse
import weco.catalogue.source_model.calm.CalmRecord
import weco.pipeline.transformer.calm.models.CalmRecordOps

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object NewTermsOfUse {
  def apply(
    conditions: Option[String],
    status: Option[AccessStatus],
    closedUntil: Option[LocalDate],
    restrictedUntil: Option[LocalDate]
  ): Option[TermsOfUse] = {
    val text = (conditions, status, closedUntil, restrictedUntil) match {
      // There's no useful information to build the note.
      case (None, None, None, None) => None

      // All we have is the free-form access condition text.
      case (Some(conditions), None, None, None) =>
        Some(conditions)

      // All we have is a date.
      case (None, None, Some(closedUntil), None) =>
        Some(s"Closed until ${closedUntil.format(displayFormat)}.")
      case (None, None, None, Some(restrictedUntil)) =>
        Some(s"Restricted until ${restrictedUntil.format(displayFormat)}.")

      case _ =>
        println(s"conditions = $conditions")
        println(s"status = $status")
        println(s"closedUntil = $closedUntil")
        println(s"restrictedUntil = $restrictedUntil")
        throw new Throwable("Unhandled!")
    }

    text.map(TermsOfUse)
  }

  // e.g. 1 January 2021
  //
  // This is the format used for dates on wellcomecollection.org.
  private val displayFormat = DateTimeFormatter.ofPattern("d MMMM yyyy")
}

object NewCalmTermsOfUse extends CalmRecordOps {
  def apply(calmRecord: CalmRecord): Option[TermsOfUse] =
    NewTermsOfUse(
      conditions = calmRecord.get("AccessConditions"),
      status = CalmAccessStatus(calmRecord),
      closedUntil = calmRecord.get("ClosedUntil").map(parseAsDate),
      restrictedUntil = calmRecord.get("UserDate1").map(parseAsDate)
    )

  // e.g. parsing dates "01/01/2039"
  private def parseAsDate(s: String): LocalDate =
    LocalDate.parse(s, DateTimeFormatter.ofPattern("d/M/yyyy"))
}
