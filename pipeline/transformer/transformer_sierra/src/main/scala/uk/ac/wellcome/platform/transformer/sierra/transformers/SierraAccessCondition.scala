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
      case (None, Some(0), Some("-"), Some("o"), NotRequestable.OpenShelves(_), Some(LocationType.OpenShelves)) =>
        (List(), ItemStatus.Available)

      // - = "available"
      // f = "Online request"
      case (None, Some(0), Some("-"), Some("f"), Requestable, Some(LocationType.ClosedStores)) |
           (Some(AccessStatus.Open), Some(0), Some("-"), Some("f"), Requestable, Some(LocationType.ClosedStores)) =>
        (
          List(AccessCondition(status = Some(AccessStatus.Open), terms = Some("Online request"))),
          ItemStatus.Available
        )

      // "b" / "c" = "as above"
      case (_, _, Some("b"), _, _, _) | (_, _, Some("c"), _, _, _) =>
        (List(), ItemStatus.Unavailable)

      // "y" = "permission required"
      // "a" = "by appointment"
      case (Some(AccessStatus.ByAppointment), _, Some("y"), Some("a"), NotRequestable.PermissionRequired, Some(LocationType.ClosedStores)) |
           (None, _, Some("y"), Some("a"), NotRequestable.PermissionRequired, Some(LocationType.ClosedStores)) =>
        (
          List(AccessCondition(status = AccessStatus.ByAppointment)),
          ItemStatus.Available
        )

      // "-" = "available"
      // "n" = "manual request"
      case (None, _, Some("-"), Some("n"), notRequestable: NotRequestable, Some(LocationType.ClosedStores)) =>
        (
          List(
            AccessCondition(
              status = Some(AccessStatus.Open),
              terms = notRequestable.message
            )
          ),
          ItemStatus.Unavailable
        )

      case (_, _, _, _, NotRequestable.ItemMissing(missingMessage), _) =>
        (
          List(
            AccessCondition(
              status = Some(AccessStatus.Unavailable),
              terms = Some(missingMessage)
            )
          ),
          ItemStatus.Unavailable
        )

      case (_, _, _, _, NotRequestable.ItemWithdrawn(withdrawnMessage), _) =>
        (
          List(
            AccessCondition(
              status = Some(AccessStatus.Unavailable),
              terms = Some(withdrawnMessage)
            )
          ),
          ItemStatus.Unavailable
        )

      // "r" = "unavailable"
      // "b" = "At digitisation"
      case (_, _, Some("r"), Some("b"), _, _) =>
        (
          List(
            AccessCondition(
              status = Some(AccessStatus.TemporarilyUnavailable),
              terms = Some("At digitisation and temporarily unavailable.")
            )
          ),
          ItemStatus.TemporarilyUnavailable
        )

      // "y" = "permission required"
      // "q" = "donor permission"
      case (Some(AccessStatus.PermissionRequired), _, Some("y"), Some("q"), NotRequestable.PermissionRequired, Some(LocationType.ClosedStores)) =>
        (
          List(
            AccessCondition(status = AccessStatus.PermissionRequired)
          ),
          ItemStatus.Available
        )

      // "h" = "closed"
      // "u" = "unavailable"
      case (Some(AccessStatus.Closed), _, Some("h"), Some("u"), NotRequestable.ItemClosed(_), Some(LocationType.ClosedStores)) =>
        (
          List(
            AccessCondition(status = AccessStatus.Closed)
          ),
          ItemStatus.Unavailable
        )

      // "6" = "restricted"
      // "f" = "Online request"
      case (Some(AccessStatus.Restricted), Some(0), Some("6"), Some("f"), Requestable, Some(LocationType.ClosedStores)) =>
        (
          List(
            AccessCondition(status = Some(AccessStatus.Restricted), terms = Some("Online request"))
          ),
          ItemStatus.Available
        )

      case other =>
        println(other)
        throw new RuntimeException(s"Unhandled case! $other")
    }
  }
}
