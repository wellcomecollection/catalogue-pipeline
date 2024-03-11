package weco.pipeline.transformer.calm.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.locations.AccessStatus
import weco.catalogue.source_model.calm.CalmRecord
import weco.pipeline.transformer.calm.models.CalmRecordOps

object CalmAccessStatus extends CalmRecordOps with Logging {
  def apply(record: CalmRecord): Option[AccessStatus] =
    record.get("AccessStatus").map(_.stripSuffix(".")) match {
      case Some("Open")               => Some(AccessStatus.Open)
      case Some("Open with advisory") => Some(AccessStatus.OpenWithAdvisory)
      case Some("Closed")             => Some(AccessStatus.Closed)
      case Some("Restricted")         => Some(AccessStatus.Restricted)
      case Some("Safeguarded")        => Some(AccessStatus.Safeguarded)
      case Some(s) if s.toLowerCase == "certain restrictions apply" =>
        Some(AccessStatus.Restricted)
      case Some(s)
          if s.toLowerCase == "restricted access (data protection act)" =>
        Some(AccessStatus.Restricted)
      case Some("By Appointment")   => Some(AccessStatus.ByAppointment)
      case Some("Donor Permission") => Some(AccessStatus.PermissionRequired)
      case Some("Cannot Be Produced") | Some("Missing") | Some(
            "Deaccessioned"
          ) =>
        Some(AccessStatus.Unavailable)
      case Some("Temporarily Unavailable") =>
        Some(AccessStatus.TemporarilyUnavailable)
      case None => None
      case status =>
        warn(s"Unrecognised AccessStatus in Calm: $status")
        None
    }
}
