package weco.pipeline.transformer.calm.transformers

import weco.catalogue.internal_model.locations.AccessStatus
import weco.catalogue.internal_model.work.TermsOfUse
import weco.catalogue.source_model.calm.CalmRecord
import weco.pipeline.transformer.calm.models.CalmRecordOps

import java.time.LocalDate

object NewTermsOfUse {
  def apply(
    conditions: Option[String],
    status: Option[AccessStatus],
    closedUntil: Option[LocalDate],
    restrictedUntil: Option[LocalDate]
  ): Option[TermsOfUse] =
    (conditions, status, closedUntil, restrictedUntil) match {
      // There's no useful information to build the note.
      case (None, None, None, None) => None

      case _ =>
        println(s"conditions = $conditions")
        println(s"status = $status")
        println(s"closedUntil = $closedUntil")
        println(s"restrictedUntil = $restrictedUntil")
        throw new Throwable("Unhandled!")
    }
}

object NewCalmTermsOfUse extends CalmRecordOps {
  def apply(calmRecord: CalmRecord): Option[TermsOfUse] =
    NewTermsOfUse(
      conditions = calmRecord.get("AccessConditions"),
      status = CalmAccessStatus(calmRecord),
      closedUntil = calmRecord.get("ClosedUntil").map(LocalDate.parse),
      restrictedUntil = calmRecord.get("UserDate1").map(LocalDate.parse)
    )
}
