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

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.util.Try

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
    location: Option[PhysicalLocationType],
    itemData: SierraItemData
  ): (AccessCondition, Option[String]) = {
    val accessCondition = createAccessCondition(
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
    holdCount: Option[Int],
    status: Option[String],
    opacmsg: Option[String],
    rulesForRequestingResult: RulesForRequestingResult,
    location: Option[PhysicalLocationType],
    itemData: SierraItemData
  ): AccessCondition =
    (holdCount, status, opacmsg, rulesForRequestingResult, location) match {

      // Items in the closed stores that are requestable get the "Online request" condition.
      //
      // Example: b18799966 / i17571170
      case (
          Some(0),
          Some(Status.Available),
          Some(OpacMsg.OnlineRequest),
          Requestable,
          Some(LocationType.ClosedStores)) =>
        AccessCondition(
          method = AccessMethod.OnlineRequest,
          status = AccessStatus.Open
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
          Some(0),
          Some(Status.Available),
          Some(OpacMsg.OpenShelves),
          NotRequestable.OnOpenShelves(_),
          Some(LocationType.OpenShelves)) =>
        AccessCondition(method = AccessMethod.OpenShelves)

      // There are some items that are labelled "bound in above" or "contained in above".
      //
      // These items aren't requestable on their own; you have to request the "primary" item.
      case (_, _, _, NotRequestable.RequestTopItem(message), _) =>
        AccessCondition(
          method = AccessMethod.NotRequestable,
          note = Some(message)
        )

      // Handle any cases that require a manual request.
      //
      // Example: b32214832 / i19389383
      case (
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
          _,
          Some(Status.Unavailable),
          Some(OpacMsg.Unavailable),
          NotRequestable.ItemUnavailable(_),
          _) =>
        AccessCondition(
          method = AccessMethod.NotRequestable,
          status = Some(AccessStatus.TemporarilyUnavailable),
          // Asked for by Victoria Sloyan in an email to Alex dated 17 Jan 2022.
          note = Some(
            "This item is undergoing internal assessment or conservation work."
          ))

      case (
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
          Some(0),
          Some(Status.Available),
          Some(OpacMsg.Restricted),
          Requestable,
          Some(LocationType.ClosedStores)) =>
        AccessCondition(
          method = AccessMethod.OnlineRequest,
          status = AccessStatus.Restricted)

      // The status "by appointment" takes precedence over "permission required".
      //
      // Examples: b32214832 / i19389383, b16576111 / 15862409
      case (
          Some(0),
          Some(Status.PermissionRequired),
          Some(OpacMsg.ByAppointment),
          NotRequestable.NoPublicMessage(_),
          Some(LocationType.ClosedStores)) =>
        AccessCondition(
          method = AccessMethod.ManualRequest,
          status = AccessStatus.ByAppointment)

      case (
          Some(0),
          Some(Status.PermissionRequired),
          Some(OpacMsg.DonorPermission),
          _: NotRequestable,
          Some(LocationType.ClosedStores)) =>
        AccessCondition(
          method = AccessMethod.ManualRequest,
          status = AccessStatus.PermissionRequired)

      // A missing status overrides all other values.
      //
      // Example: b10379198 / i10443861
      case (
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
      // Note that an item on hold goes through two stages:
      //
      //  1. A reader places a hold, but the item is still in the store.
      //     The status is still "-" (Available)
      //  2. A staff member collects the item from the store, and places it on the holdshelf
      //     Then the status becomes "!" (On holdshelf)  This is reflected in the rules for requesting.
      //
      // It is possible for an item to have a non-zero hold count but still be available
      // for requesting, e.g. some of our long-lived test holds didn't get cleared properly.
      // If an item seems to be stuck on a non-zero hold count, ask somebody to check Sierra.
      case (Some(holdCount), _, _, _, Some(LocationType.ClosedStores))
          if holdCount > 0 =>
        AccessCondition(
          method = AccessMethod.NotRequestable,
          status = Some(AccessStatus.TemporarilyUnavailable),
          note = Some(
            "Item is in use by another reader. Please ask at Library Enquiry Desk.")
        )

      case (
          _,
          _,
          _,
          NotRequestable.InUseByAnotherReader(_),
          Some(LocationType.ClosedStores)) =>
        AccessCondition(
          method = AccessMethod.NotRequestable,
          status = Some(AccessStatus.TemporarilyUnavailable),
          note = Some(
            "Item is in use by another reader. Please ask at Library Enquiry Desk.")
        )

      // Note that items can borrowed even if they're on the open shelves, but this
      // isn't something that's available to regular library members, so don't be
      // specific about why the item is unavailable.
      case (
          _,
          _,
          _,
          NotRequestable.InUseByAnotherReader(_),
          Some(LocationType.OpenShelves)) =>
        val noteText = itemData.dueDate match {
          case Some(d) =>
            s"This item is temporarily unavailable. It is due for return on ${d.format(displayFormat)}."
          case _ => "This item is temporarily unavailable."
        }

        AccessCondition(
          method = AccessMethod.OpenShelves,
          status = Some(AccessStatus.TemporarilyUnavailable),
          note = Some(noteText)
        )

      // When an item is on display in an exhibition, it is not available for request.
      // In this case, the Reserves Note(s) should give some more detail.
      case (_, _, _, _, Some(LocationType.OnExhibition))
          if itemData.varFields.withFieldTag("r").nonEmpty =>
        // Reserves Notes normally contain text at either end that is not
        // relevant for end users wishing to understand how to access the item.
        // (https://documentation.iii.com/sierrahelp/Content/sgcir/sgcir_course_updaterecs.html)
        // These include the date it went on/off reserve, and the number of times it circulated
        // Remove that text before storing the remainder as an access condition note.
        val sanitisedReservesNotes =
          for (source_str <- itemData.varFields.withFieldTag("r").contents)
            yield
              source_str
                .replaceFirst(
                  "^\\d\\d-\\d\\d-\\d\\d (ON|OFF) RESERVE FOR ",
                  ""
                )
                .replaceAll(" CIRCED \\d+ TIMES", "")

        AccessCondition(
          method = AccessMethod.NotRequestable,
          note = Some(sanitisedReservesNotes.mkString("<br />"))
        )

      // If we can't work out how this item should be handled, then let's mark it
      // as unavailable for now.
      //
      // TODO: We should work with the Collections team to better handle any records
      // that are hitting this branch.
      case (holdCount, status, opacmsg, isRequestable, location) =>
        warn(
          s"Unable to assign access status for item ${itemData.id.withCheckDigit}: " +
            s"holdCount=$holdCount, status=$status, " +
            s"opacmsg=$opacmsg, isRequestable=$isRequestable, location=$location"
        )

        AccessCondition(
          method = AccessMethod.NotRequestable,
          note = Some(
            s"""This item cannot be requested online. Please contact <a href="mailto:library@wellcomecollection.org">library@wellcomecollection.org</a> for more information.""")
        )
    }

  // e.g. 1 January 2021
  private val displayFormat = DateTimeFormatter.ofPattern("d MMMM yyyy")

  implicit class ItemDataAccessOps(itemData: SierraItemData) {
    def status: Option[String] =
      itemData.fixedFields.get("88").map { _.value.trim }

    def opacmsg: Option[String] =
      itemData.fixedFields.get("108").map { _.value.trim }

    // e.g. 2020-09-01
    private val dueDateFormat = DateTimeFormatter.ofPattern("yyyy-M-d")

    def dueDate: Option[LocalDate] =
      itemData.fixedFields
        .get("65")
        .map { _.value.trim }
        .map {
          // e.g. 2020-09-01T03:00:00Z
          _.split("T").head
        }
        .flatMap { s =>
          Try(LocalDate.parse(s, dueDateFormat)).toOption
        }
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
