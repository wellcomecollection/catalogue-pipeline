package uk.ac.wellcome.platform.transformer.calm.transformers

import uk.ac.wellcome.models.work.internal.{
  AccessCondition,
  AccessStatus,
  IdState,
  Item,
  LocationType,
  PhysicalLocation
}
import uk.ac.wellcome.models.work.internal.result._
import uk.ac.wellcome.platform.transformer.calm.CalmRecordOps
import weco.catalogue.source_model.calm.CalmRecord

object CalmItems extends CalmRecordOps {
  def apply(record: CalmRecord): Result[List[Item[IdState.Unminted]]] =
    for {
      status <- accessStatus(record)

      items = List(
        Item(
          title = None,
          locations = List(physicalLocation(record, status))
        )
      )
    } yield items

  private def physicalLocation(record: CalmRecord,
                               status: Option[AccessStatus]): PhysicalLocation =
    PhysicalLocation(
      locationType = LocationType.ClosedStores,
      label = "Closed stores Arch. & MSS",
      accessConditions = accessCondition(record, status).filterEmpty.toList
    )

  private def accessCondition(record: CalmRecord,
                              status: Option[AccessStatus]): AccessCondition =
    AccessCondition(
      status = status,
      terms = record.getJoined("AccessConditions"),
      to = status match {
        case Some(AccessStatus.Closed)     => record.get("ClosedUntil")
        case Some(AccessStatus.Restricted) => record.get("UserDate1")
        case _                             => None
      }
    )

  private def accessStatus(record: CalmRecord): Result[Option[AccessStatus]] =
    record
      .get("AccessStatus")
      .map(AccessStatus(_))
      .toResult
}
