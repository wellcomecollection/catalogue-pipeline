package weco.catalogue.source_model.sierra.rules

import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessStatus,
  ItemStatus,
  LocationType,
  PhysicalLocationType
}
import weco.catalogue.source_model.sierra.SierraItemData
import weco.catalogue.source_model.sierra.source.{
  OpacMsg,
  SierraQueryOps,
  Status
}

/** There are multiple sources of truth for item information in Sierra, and whether
  * a given item can be requested online.
  *
  * This object tries to create a single, consistent view of this data.
  * It returns two values:
  *
  *   - An access condition that can be added to a location on an Item.
  *     This would be set in the Catalogue API.
  *   - An ItemStatus that returns a simpler "is this available right now".
  *     This would be returned from the items API with the most up-to-date
  *     data from Sierra.
  *
  */
object SierraItemAccess extends SierraQueryOps {
  def apply(
    bibStatus: Option[AccessStatus],
    location: Option[PhysicalLocationType],
    itemData: SierraItemData
  ): (Option[AccessCondition], ItemStatus) = {
    val holdCount = itemData.holdCount
    val status = itemData.status
    val opacmsg = itemData.opacmsg
    val isRequestable = SierraRulesForRequesting(itemData)

    (bibStatus, holdCount, status, opacmsg, isRequestable, location) match {

      // Items on the closed stores that are requestable get the "Online request" condition.
      //
      // Example: b18799966 / i17571170
      case (None, Some(0), Some(Status.Available), Some(OpacMsg.OnlineRequest), Requestable, Some(LocationType.ClosedStores)) =>
        val ac = AccessCondition(
          terms = Some("Online request"),
          note = itemData.displayNote
        )

        (Some(ac), ItemStatus.Available)

      case other =>
        println(s"@@ $other @@")
        throw new Throwable("Unhandled!!!")
        (None, ItemStatus.Unavailable)
    }
  }

  implicit class ItemDataAccessOps(itemData: SierraItemData) {
    def status: Option[String] =
      itemData.fixedFields.get("88").map { _.value.trim }

    def opacmsg: Option[String] =
      itemData.fixedFields.get("108").map { _.value.trim }
  }
}
