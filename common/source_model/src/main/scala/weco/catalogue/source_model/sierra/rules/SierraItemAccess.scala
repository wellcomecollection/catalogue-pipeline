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

      // Items on the open shelves don't have any access conditions.
      //
      // We could add an access status of "Open" here, but it feels dubious to be
      // synthesising access information that doesn't come from the source records.
      //
      // Note: We create an AccessCondition here so we can carry information from the
      // display note.  This is used sparingly, but occasionally contains useful information
      // for readers, e.g.
      //
      //      Shelved at the end of the Quick Ref. section with the oversize Quick Ref. books.
      //
      // Example: b1659504x / i15894897
      case (
        None,
        Some(0),
        Some(Status.Available),
        Some(OpacMsg.OpenShelves),
        NotRequestable.OnOpenShelves(_),
        Some(LocationType.OpenShelves)) =>
        val ac = itemData.displayNote.map { note => AccessCondition(note = Some(note)) }
        (ac, ItemStatus.Available)

      // There are some items that are labelled "bound in above" or "contained in above".
      //
      // These items aren't requestable on their own; you have to request the "primary" item.
      case (None, _, _, _, NotRequestable.RequestTopItem(message), _) =>
        (Some(AccessCondition(terms = Some(message))), ItemStatus.Unavailable)

      // Handle any cases that require a manual request.
      //
      // Example: b32214832 / i19389383
      case (
        None,
        Some(0),
        Some(Status.Available),
        Some(OpacMsg.ManualRequest),
        NotRequestable.NeedsManualRequest(_),
        Some(LocationType.ClosedStores)) =>
        (
          Some(
            AccessCondition(
              terms = Some("Manual request"),
              note = itemData.displayNote)),
          ItemStatus.Available)

      // Handle any cases where the item is closed.
      //
      // We don't show the text from rules for requesting -- it's not saying anything
      // that you can't work out from the AccessStatus.
      //
      // Examples: b20657365 / i18576503, b1899457x / i17720734
      case (
        Some(AccessStatus.Closed),
        _,
        Some(Status.Closed),
        Some(OpacMsg.Unavailable),
        NotRequestable.ItemClosed(_),
        Some(LocationType.ClosedStores)) =>
        (
          Some(
            AccessCondition(
              status = Some(AccessStatus.Closed),
              note = itemData.displayNote)),
          ItemStatus.Unavailable)

      // Handle any cases where the item is explicitly unavailable.
      case (
        None,
        _,
        Some(Status.Unavailable),
        Some(OpacMsg.Unavailable),
        NotRequestable.ItemUnavailable(_),
        _) =>
        (
          Some(
            AccessCondition(
              status = Some(AccessStatus.Unavailable),
              note = itemData.displayNote)),
          ItemStatus.Unavailable)

      case (
        None,
        _,
        Some(Status.Unavailable),
        Some(OpacMsg.AtDigitisation),
        NotRequestable.ItemUnavailable(_),
        _) =>
        (
          Some(
            AccessCondition(
              status = Some(AccessStatus.TemporarilyUnavailable),
              terms = Some("At digitisation and temporarily unavailable"),
              note = itemData.displayNote)),
          ItemStatus.TemporarilyUnavailable)

      // An item which is restricted can be requested online -- the user will have to fill in
      // any paperwork when they actually visit the library.
      //
      // Example: b29459126 / i19023340
      case (
        Some(AccessStatus.Restricted),
        Some(0),
        Some(Status.Restricted),
        Some(OpacMsg.OnlineRequest),
        Requestable,
        Some(LocationType.ClosedStores)) =>
        (
          Some(
            AccessCondition(
              status = Some(AccessStatus.Restricted),
              terms = Some("Online request"),
              note = itemData.displayNote)),
          ItemStatus.Available)

      // The status "by appointment" takes precedence over "permission required".
      //
      // Examples: b32214832 / i19389383, b16576111 / 15862409
      case (
        bibStatus,
        Some(0),
        Some(Status.PermissionRequired),
        Some(OpacMsg.ByAppointment),
        NotRequestable.NoReason,
        Some(LocationType.ClosedStores))
        if bibStatus.isEmpty || bibStatus.contains(AccessStatus.ByAppointment) || bibStatus
          .contains(AccessStatus.PermissionRequired) =>
        (
          Some(
            AccessCondition(
              status = Some(AccessStatus.ByAppointment),
              note = itemData.displayNote)),
          ItemStatus.Available)

      // A missing status overrides all other values.
      //
      // Example: b10379198 / i10443861
      case (
        _,
        _,
        Some(Status.Missing),
        _,
        NotRequestable.ItemMissing(message),
        _) =>
        (
          Some(
            AccessCondition(
              status = Some(AccessStatus.Unavailable),
              terms = Some(message))),
          ItemStatus.Unavailable)

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
