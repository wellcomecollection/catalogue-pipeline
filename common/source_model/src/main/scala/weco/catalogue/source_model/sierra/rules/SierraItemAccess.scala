package weco.catalogue.source_model.sierra.rules

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessMethod,
  AccessStatus,
  LocationType,
  PhysicalLocationType
}
import weco.catalogue.source_model.sierra.source.{OpacMsg, Status}
import weco.sierra.models.SierraQueryOps
import weco.sierra.models.data.SierraItemData
import weco.sierra.models.identifiers.SierraBibNumber
import weco.sierra.models.marc.FixedField

/** There are multiple sources of truth for item information in Sierra, and whether
  * a given item can be requested online.
  *
  * This object tries to create a single, consistent view of this data.
  * It returns three values:
  *
  *   - An access condition that can be added to a location on an Item.
  *     This would be set in the Catalogue API.
  *   - A note that can be used to distinguish between different items.
  *     This should be copied to the top-level item model.
  *   - An ItemStatus that returns a simpler "is this available right now".
  *     This would be returned from the items API with the most up-to-date
  *     data from Sierra.
  *
  */
object SierraItemAccess extends SierraQueryOps with Logging {
  def apply(
    bibId: SierraBibNumber,
    bibStatus: Option[AccessStatus],
    location: Option[PhysicalLocationType],
    itemData: SierraItemData
  ): (AccessCondition, Option[String]) = {
    val accessCondition = createAccessCondition(
      bibId = bibId,
      bibStatus = bibStatus,
      holdCount = itemData.holdCount,
      status = itemData.status,
      opacmsg = itemData.opacmsg,
      rulesForRequestingResult = SierraRulesForRequesting(itemData),
      location = location,
      itemData = itemData
    )

    (accessCondition, itemData.displayNote) match {
      // If the item note is already on the access condition, we don't need to copy it.
      case (ac, displayNote) if ac.note == displayNote =>
        (ac, None)

      // If the item note is an access note but there's already an access note on the
      // access condition, we discard the item note.
      //
      // Otherwise, we copy the item note onto the access condition.
      case (ac, Some(displayNote))
          if ac.note.isDefined && displayNote.isAccessNote =>
        (ac, None)
      case (ac, Some(displayNote))
          if ac.note.isEmpty && displayNote.isAccessNote =>
        (ac.copy(note = Some(displayNote)), None)

      // If the item note is nothing to do with the access condition, we return it to
      // be copied onto the item.
      case (ac, displayNote) => (ac, displayNote)
    }
  }

  private def createAccessCondition(
    bibId: SierraBibNumber,
    bibStatus: Option[AccessStatus],
    holdCount: Option[Int],
    status: Option[String],
    opacmsg: Option[String],
    rulesForRequestingResult: RulesForRequestingResult,
    location: Option[PhysicalLocationType],
    itemData: SierraItemData
  ): AccessCondition =
    (bibStatus, holdCount, status, opacmsg, rulesForRequestingResult, location) match {

      // Items in the closed stores that are requestable get the "Online request" condition.
      //
      // Example: b18799966 / i17571170
      case (
          bibStatus,
          Some(0),
          Some(Status.Available),
          Some(OpacMsg.OnlineRequest),
          Requestable,
          Some(LocationType.ClosedStores))
          if bibStatus.isEmpty || bibStatus.contains(AccessStatus.Open) =>
        AccessCondition(
          method = AccessMethod.OnlineRequest,
          status = bibStatus
        )

      // Note: it is possible for individual items within a restricted bib to be available
      // online, e.g. in archives.  The "restricted" on the bib applies to the archive as
      // a whole, but individual files may be open.
      //
      // Consider an archive with three items:
      //
      //      Item 1 = Open
      //      Item 2 = contains sensitive material, so restricted
      //      Item 3 = Open
      //
      // Then the top-level bib status would be "certain restrictions apply" for the
      // archive as a whole, referring to item 2 -- but items 1 and 3 would be open.
      //
      // This is distinct from the case above because we want to replace the bib-level
      // status with "Open", rather than pass it through.
      //
      // Example: b1842941 / i17286803
      case (
          Some(AccessStatus.Restricted),
          Some(0),
          Some(Status.Available),
          Some(OpacMsg.OnlineRequest),
          Requestable,
          Some(LocationType.ClosedStores)) =>
        AccessCondition(
          method = AccessMethod.OnlineRequest,
          status = AccessStatus.Open,
        )

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
        AccessCondition(method = AccessMethod.OpenShelves)

      // There are some items that are labelled "bound in above" or "contained in above".
      //
      // These items aren't requestable on their own; you have to request the "primary" item.
      case (None, _, _, _, NotRequestable.RequestTopItem(message), _) =>
        AccessCondition(
          method = AccessMethod.NotRequestable,
          note = Some(message)
        )

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
        // Some items like this have a display note that explains how the manual request
        // works, e.g.
        //
        //      Email library@wellcomecollection.org to tell us why you need access.
        //      We'll reply within a week.
        //
        // If such a note is present, put it in the note field on the access condition.
        val accessNote =
          itemData.displayNote match {
            case Some(note) if note.isManualRequestNote =>
              Some(note)

            case _ => None
          }

        AccessCondition(method = AccessMethod.ManualRequest, note = accessNote)

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
          locationType)
          if locationType.isEmpty || locationType.contains(
            LocationType.ClosedStores) =>
        AccessCondition(
          method = AccessMethod.NotRequestable,
          status = AccessStatus.Closed)

      // Handle any cases where the item is explicitly unavailable.
      case (
          status,
          _,
          Some(Status.Unavailable),
          Some(OpacMsg.Unavailable),
          NotRequestable.ItemUnavailable(_),
          _)
          if status.isEmpty || status.contains(
            AccessStatus.TemporarilyUnavailable) =>
        AccessCondition(
          method = AccessMethod.NotRequestable,
          status = AccessStatus.Unavailable)

      case (
          None,
          _,
          Some(Status.Unavailable),
          Some(OpacMsg.AtDigitisation),
          NotRequestable.ItemUnavailable(_),
          _) =>
        AccessCondition(
          method = AccessMethod.NotRequestable,
          status = Some(AccessStatus.TemporarilyUnavailable),
          note =
            Some("This item is being digitised and is currently unavailable.")
        )

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
        AccessCondition(
          method = AccessMethod.OnlineRequest,
          status = AccessStatus.Restricted)

      // The status "by appointment" takes precedence over "permission required".
      //
      // Examples: b32214832 / i19389383, b16576111 / 15862409
      case (
          bibStatus,
          Some(0),
          Some(Status.PermissionRequired),
          Some(OpacMsg.ByAppointment),
          NotRequestable.NoPublicMessage(_),
          Some(LocationType.ClosedStores))
          if bibStatus.isEmpty || bibStatus.contains(AccessStatus.ByAppointment) || bibStatus
            .contains(AccessStatus.PermissionRequired) =>
        AccessCondition(
          method = AccessMethod.ManualRequest,
          status = AccessStatus.ByAppointment)

      case (
          bibStatus,
          Some(0),
          Some(Status.PermissionRequired),
          Some(OpacMsg.DonorPermission),
          _: NotRequestable,
          Some(LocationType.ClosedStores))
          if bibStatus.isEmpty || bibStatus.contains(
            AccessStatus.PermissionRequired) =>
        AccessCondition(
          method = AccessMethod.ManualRequest,
          status = AccessStatus.PermissionRequired)

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
        AccessCondition(
          method = AccessMethod.NotRequestable,
          status = Some(AccessStatus.Unavailable),
          note = Some(message))

      // A withdrawn status also overrides all other values.
      case (
          _,
          _,
          Some(Status.Withdrawn),
          _,
          NotRequestable.ItemWithdrawn(message),
          _) =>
        AccessCondition(
          method = AccessMethod.NotRequestable,
          status = Some(AccessStatus.Unavailable),
          note = Some(message))

      // If an item is on hold for another reader, it can't be requested -- even
      // if it would ordinarily be requestable.
      //
      // We try to work out what the access condition would have been before the item
      // was on hold -- this allows us to preserve the original access method.
      //
      // e.g. if an item is usually an "online request" but is temporarily on hold for
      // somebody else, we can show that it's an online request at other times.
      //
      case (
          None,
          holdCount,
          _,
          _,
          rulesForRequestingResult,
          Some(LocationType.ClosedStores))
          if isOnHold(holdCount, rulesForRequestingResult) =>
        val inferredItemData = itemData.copy(
          holdCount = Some(0),
          fixedFields = itemData.fixedFields ++ Map(
            "87" -> FixedField(label = "LOANRULE", value = "0"),
            "88" -> FixedField(
              label = "STATUS",
              value = "-",
              display = "Available"))
        )

        val rulesForRequestingResult = SierraRulesForRequesting(
          inferredItemData)

        // Make sure we only recurse here once, not infinitely many times.
        assert(!rulesForRequestingResult.isInstanceOf[NotRequestable.OnHold])
        assert(!isOnHold(inferredItemData.holdCount, rulesForRequestingResult))

        val originalAccessCondition =
          createAccessCondition(
            bibId = bibId,
            bibStatus = bibStatus,
            holdCount = Some(0),
            status = Some(Status.Available),
            opacmsg = inferredItemData.opacmsg,
            rulesForRequestingResult = rulesForRequestingResult,
            location = location,
            itemData = inferredItemData
          )

        originalAccessCondition.copy(
          status = Some(AccessStatus.TemporarilyUnavailable),
          note = Some(
            "Item is in use by another reader. Please ask at Enquiry Desk.")
        )

      // If we can't work out how this item should be handled, then let's mark it
      // as unavailable for now.
      //
      // TODO: We should work with the Collections team to better handle any records
      // that are hitting this branch.  Sending readers to Encore isn't a long-term
      // solution.  Remove this link when Encore goes away.
      //
      // Note: once you remove the link, you can also remove the bibId passed into
      // this apply() method.
      case (bibStatus, holdCount, status, opacmsg, isRequestable, location) =>
        warn(
          s"Unable to assign access status for item ${itemData.id.withCheckDigit}: " +
            s"bibStatus=$bibStatus, holdCount=$holdCount, status=$status, " +
            s"opacmsg=$opacmsg, isRequestable=$isRequestable, location=$location"
        )

        AccessCondition(
          method = AccessMethod.NotRequestable,
          note = Some(
            s"""This item cannot be requested online. Please contact <a href="mailto:library@wellcomecollection.org">library@wellcomecollection.org</a> for more information.""")
        )
    }

  // Note that an item on hold goes through two stages:
  //
  //  1. A reader places a hold, but the item is still in the store.
  //     The status is still "-" (Available)
  //  2. A staff member collects the item from the store, and places it on the holdshelf
  //     Then the status becomes "!" (On holdshelf)  This is reflected in the rules for requesting.
  //
  private def isOnHold(
    holdCount: Option[Int],
    rulesForRequestingResult: RulesForRequestingResult): Boolean =
    (holdCount, rulesForRequestingResult) match {
      case (Some(holdCount), _) if holdCount > 0 => true
      case (_, NotRequestable.OnHold(_))         => true
      case _                                     => false
    }

  implicit class ItemDataAccessOps(itemData: SierraItemData) {
    def status: Option[String] =
      itemData.fixedFields.get("88").map { _.value.trim }

    def opacmsg: Option[String] =
      itemData.fixedFields.get("108").map { _.value.trim }
  }

  // The display note field has been used for multiple purposes, in particular:
  //
  //  1) Distinguishing between different copies of an item, so people know
  //     which item to request, e.g. "impression lacking lettering"
  //  2) Recording information about how to access the item, e.g. "please email us"
  //
  // This method uses a few heuristics to guess whether a given note is actually information
  // about access that we should copy to the "terms" field.
  private implicit class NoteStringOps(note: String) {
    def isManualRequestNote: Boolean =
      containsAnyOf(
        "needs to be ordered",
        "to view this item",
        "to view it",
        "physical access",
        "physical copy",
        "why you need access",
        "details of your request",
        "to view please contact",
        "if you would like to see"
      )

    def isAccessNote: Boolean =
      containsAnyOf(
        "unavailable",
        "access",
        "please contact",
        "@wellcomecollection.org",
        "offsite",
        "shelved at"
      )

    private def containsAnyOf(substrings: String*): Boolean =
      substrings.exists(note.toLowerCase.contains(_))
  }
}
