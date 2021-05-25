package uk.ac.wellcome.platform.transformer.sierra.transformers

import weco.catalogue.internal_model.locations.{
  AccessCondition,
  LocationType,
  PhysicalLocationType
}
import weco.catalogue.source_model.sierra.source.SierraQueryOps
import weco.catalogue.source_model.sierra.{
  OpenShelvesNotRequestable,
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
        (List(), ItemStatus.Available)

      case other =>
        println(other)
        throw new RuntimeException(s"Unhandled case! $other")
    }
  }
}
