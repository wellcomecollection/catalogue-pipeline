package uk.ac.wellcome.platform.transformer.sierra.transformers

import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessStatus,
  LocationType,
  PhysicalLocationType
}
import weco.catalogue.source_model.sierra.source.SierraQueryOps
import weco.catalogue.source_model.sierra.{
  NotRequestable,
  OpenShelvesNotRequestable,
  PermissionRequiredNotRequestable,
  Requestable,
  SierraBibData,
  SierraBibNumber,
  SierraItemData,
  SierraItemNumber,
  SierraRulesForRequesting
}

sealed trait ItemStatus

object ItemStatus {
  case object Available extends ItemStatus
  case object TemporarilyUnavailable extends ItemStatus
  case object Unavailable extends ItemStatus
}

object SierraAccessCondition extends SierraQueryOps {
  def apply(bibId: SierraBibNumber, bibData: SierraBibData, itemId: SierraItemNumber, itemData: SierraItemData): (List[AccessCondition], ItemStatus) = {
    val bibAccessStatus = SierraAccessStatus.forBib(bibId, bibData)
    val holdCount = itemData.holdCount
    val status = itemData.status
    val opacmsg = itemData.opacmsg
    val isRequestable = SierraRulesForRequesting(itemData)
    val location: Option[PhysicalLocationType] = itemData.location.map { _.name }.flatMap { SierraPhysicalLocationType.fromName(itemId, _) }

    (bibAccessStatus, holdCount, status, opacmsg, isRequestable, location) match {
      // - = "available"
      // o = "Open shelves"
      case (None, Some(0), Some("-"), Some("o"), OpenShelvesNotRequestable(_), Some(LocationType.OpenShelves)) =>
        (List(), ItemStatus.Available)

      // - = "available"
      // f = "Online request"
      case (None, Some(0), Some("-"), Some("f"), Requestable, Some(LocationType.ClosedStores)) =>
        (
          List(AccessCondition(status = Some(AccessStatus.Open), note = Some("Online request"))),
          ItemStatus.Available
        )

      // "b" = "as above"
      case (_, _, Some("b"), _, _, _) =>
        (List(), ItemStatus.Unavailable)

      // "y" = "permission required"
      // "a" = "by appointment"
      case (Some(AccessStatus.ByAppointment), Some(0), Some("y"), Some("a"), PermissionRequiredNotRequestable, Some(LocationType.ClosedStores)) =>
        (
          List(AccessCondition(status = AccessStatus.ByAppointment)),
          ItemStatus.Available
        )

      // "-" = "available"
      // "n" = "manual request"
      case (None, Some(0), Some("-"), Some("n"), notRequestable: NotRequestable, Some(LocationType.ClosedStores)) =>
        (
          List(
            AccessCondition(
              status = Some(AccessStatus.Open),
              note = Some("Manual request"),
              terms = notRequestable.message
            )
          ),
          ItemStatus.Unavailable
        )

      case other =>
        println(other)
        throw new RuntimeException(s"Unhandled case! $other")
    }
  }
}
