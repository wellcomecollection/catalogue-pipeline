package uk.ac.wellcome.platform.transformer.calm.transformers

import uk.ac.wellcome.platform.transformer.calm.CalmRecordOps
import weco.catalogue.internal_model.locations.AccessStatus
import weco.catalogue.internal_model.work.TermsOfUse
import weco.catalogue.source_model.calm.CalmRecord

object CalmTermsOfUse extends CalmRecordOps {
  def apply(record: CalmRecord): Option[TermsOfUse] = {
    val accessConditions = record.getJoined("AccessConditions")

    val accessStatus = getAccessStatus(record)

    val closedUntil = record.get("ClosedUntil")
    val restrictedUntil = record.get("RestrictedUntil")

    val terms =
      (accessConditions, accessStatus, closedUntil, restrictedUntil) match {
        // If there are conditions and no dates, we
        case (Some(conditions), Some(status), None, None) =>
          Some(s"$conditions ${status.label}.")

        case _ => throw new NotImplementedError(s"record = $record")
      }

    terms.map(TermsOfUse)
  }

  private def getAccessStatus(record: CalmRecord): Option[AccessStatus] =
    record.get("AccessStatus") match {
      case Some("Open")       => Some(AccessStatus.Open)
      case Some("Closed")     => Some(AccessStatus.Closed)
      case Some("Restricted") => Some(AccessStatus.Restricted)
      case _                  => None
    }
}
