package uk.ac.wellcome.platform.transformer.sierra.transformers

import weco.catalogue.internal_model.locations.{AccessCondition, AccessStatus, LocationType, PhysicalLocationType}
import weco.catalogue.source_model.sierra.NotRequestable.ItemUnavailable
import weco.catalogue.source_model.sierra.marc.VarField
import weco.catalogue.source_model.sierra.source.SierraQueryOps
import weco.catalogue.source_model.sierra._

sealed trait ItemStatus

object ItemStatus {
  case object Available extends ItemStatus
  case object TemporarilyUnavailable extends ItemStatus
  case object Unavailable extends ItemStatus
}

object SierraAccessCondition extends SierraQueryOps {
  case object Status {
    val Available = "-"
    val PermissionRequired = "y"
    val Missing = "m"
    val Unavailable = "r"
    val Closed = "h"
    val Restricted = "6"
    val OnHoldshelf = "!"
  }

  object OpacMsg {
    val OnlineRequest = "f"
    val ManualRequest = "n"
    val OpenShelves = "o"
    val ByAppointment = "a"
    val AtDigitisation = "b"
    val DonorPermission = "q"
    val Unavailable = "u"
    val StaffUseOnly = "s"
    val AskAtDesk = "i"
  }

  def apply(bibId: SierraBibNumber, bibData: SierraBibData, itemId: SierraItemNumber, itemData: SierraItemData): (List[AccessCondition], ItemStatus) = {
    val holdCount = itemData.holdCount
    val status = itemData.status
    val opacmsg = itemData.opacmsg
    val isRequestable = SierraRulesForRequesting(itemData)

    // Note: When we wire up these into the items/locations code, we'll pass
    // in these values rather than re-parse them, but this works well enough
    // for the test harness.
    val bibAccessStatus = SierraAccessStatus.forBib(bibId, bibData)
    val location: Option[PhysicalLocationType] = itemData
      .location.map { _.name }
      .flatMap { SierraPhysicalLocationType.fromName(itemId, _) }

    val displayNote = itemData.varFields
      .filter { _.fieldTag.contains("n") }
      .flatMap {
        case VarField(Some(content), _, _, _, _, Nil) => List(content)
        case VarField(None, _, _, _, _, subfields) =>
          subfields.withTag("a").map { _.content }
        case vf => throw new Throwable(s"Unable to parse content from display note: $vf")
      }
      .distinct
      .mkString(" ")

    val maybeDisplayNote = if (displayNote.isEmpty) None else Some(displayNote)

    (bibAccessStatus, holdCount, status, opacmsg, isRequestable, location) match {

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
      case (None, Some(0), Some(Status.Available), Some(OpacMsg.OpenShelves), NotRequestable.OpenShelves(_), Some(LocationType.OpenShelves)) =>
        maybeDisplayNote match {
          case Some(note) => (List(AccessCondition(note = Some(note))), ItemStatus.Available)
          case None => (List(), ItemStatus.Available)
        }

      // Items on the closed stores that are requestable get the "Online request" condition.
      //
      // Example: b18799966 / i17571170, b18974946 / i1771276
      case (bibStatus, Some(0), Some(Status.Available), Some(OpacMsg.OnlineRequest), Requestable, Some(LocationType.ClosedStores))
          if bibStatus.isEmpty || bibStatus.contains(AccessStatus.Open) =>
        (List(AccessCondition(status = bibStatus, terms = Some("Online request"), note = maybeDisplayNote)), ItemStatus.Available)

      // An item which is restricted can be requested online -- the user will have to fill in
      // any paperwork when they actually visit the library.
      //
      // Example: b29459126 / i19023340
      case (Some(AccessStatus.Restricted), Some(0), Some(Status.Restricted), Some(OpacMsg.OnlineRequest), Requestable, Some(LocationType.ClosedStores)) =>
        (List(AccessCondition(status = Some(AccessStatus.Restricted), terms = Some("Online request"), note = maybeDisplayNote)), ItemStatus.Available)

      // The status "by appointment" takes precedence over "permission required".
      //
      // Examples: b32214832 / i19389383, b16576111 / 15862409
      case (bibStatus, Some(0), Some(Status.PermissionRequired), Some(OpacMsg.ByAppointment), NotRequestable.PermissionRequired, Some(LocationType.ClosedStores))
          if bibStatus.isEmpty || bibStatus.contains(AccessStatus.ByAppointment) || bibStatus.contains(AccessStatus.PermissionRequired) =>
        (List(AccessCondition(status = Some(AccessStatus.ByAppointment), note = maybeDisplayNote)), ItemStatus.Available)

      // Handle any cases that require a manual request.
      //
      // Example: b32214832 / i19389383
      case (None, Some(0), Some(Status.Available), Some(OpacMsg.ManualRequest), NotRequestable.ManualRequest(_), Some(LocationType.ClosedStores)) |
           (None, Some(0), Some(Status.Available), Some(OpacMsg.AskAtDesk), NotRequestable.ManualRequest(_), Some(LocationType.ClosedStores)) =>
        (List(AccessCondition(terms = Some("Please complete a manual request slip.  This item cannot be requested online."), note = maybeDisplayNote)), ItemStatus.Available)

      // A missing status overrides all other values.
      //
      // Example: b10379198 / i10443861
      case (_, _, Some(Status.Missing), _, NotRequestable.ItemMissing(message), _) =>
        (List(AccessCondition(status = Some(AccessStatus.Unavailable), terms = Some(message))), ItemStatus.Unavailable)

      // If an item is at digitisation, then it's temporarily unavailable -- we expect
      // it will come back to the stores at some point.
      //
      // Note: the omission of the display note here is deliberately.
      //
      // Example: b14465978 / i13753228
      case (_, _, Some(Status.Unavailable), Some(OpacMsg.AtDigitisation), _: NotRequestable, _) =>
        (List(AccessCondition(status = Some(AccessStatus.TemporarilyUnavailable), terms = Some("At digitisation and temporarily unavailable."))), ItemStatus.TemporarilyUnavailable)

      // An item may also be unavailable for other reasons.
      case (Some(AccessStatus.TemporarilyUnavailable), _, Some(Status.Unavailable), Some(OpacMsg.Unavailable), ItemUnavailable(_), _) =>
        (List(AccessCondition(status = Some(AccessStatus.TemporarilyUnavailable), note = maybeDisplayNote)), ItemStatus.TemporarilyUnavailable)

      case (None, _, Some(Status.Unavailable), Some(OpacMsg.Unavailable), ItemUnavailable(_), _) =>
        (List(AccessCondition(status = Some(AccessStatus.Unavailable), note = maybeDisplayNote)), ItemStatus.Unavailable)

      // If an item requires permission to view, it's not requestable online.
      //
      // Example: b19346955 / i17948149
      case (Some(AccessStatus.PermissionRequired), _, Some(Status.PermissionRequired), Some(OpacMsg.DonorPermission), NotRequestable.PermissionRequired, Some(LocationType.ClosedStores)) =>
        (List(AccessCondition(status = AccessStatus.PermissionRequired)), ItemStatus.Available)

      // If an item is closed, it's not requestable online.
      //
      // We don't show the text from rules for requesting -- it's not saying anything
      // that you can't work out from the AccessStatus.
      //
      // Examples: b20657365 / i18576503, b1899457x / i17720734
      case (Some(AccessStatus.Closed), _, Some(Status.Closed), Some(OpacMsg.Unavailable), NotRequestable.ItemClosed(_), locationType)
          if locationType.isEmpty || locationType.contains(LocationType.ClosedStores) =>
        (List(AccessCondition(status = AccessStatus.Closed)), ItemStatus.Unavailable)

      // An item that can't be requested online but is viewable by appointment.
      //
      // Example: b16561909 / i15842824
      case (None, _, Some(Status.Available), Some(OpacMsg.ByAppointment), OtherNotRequestable(message), Some(LocationType.ClosedStores)) =>
        (List(AccessCondition(status = Some(AccessStatus.ByAppointment), terms = message)), ItemStatus.Available)

      // An item for staff use only can't be requested online.
      //
      // Example: b20164579 / i18446383
      case (None, _, Some(Status.Available), Some(OpacMsg.StaffUseOnly), OtherNotRequestable(None), Some(LocationType.ClosedStores)) =>
        (List(AccessCondition(status = Some(AccessStatus.Unavailable), terms = Some("Staff use only"))), ItemStatus.Unavailable)

      // An item on exhibition is temporarily unavailable.
      //
      // Note: The rules for requesting give you a message about this item being in use by another reader;
      // this is technically true (it's checked out to the exhibitions patron ID) but not useful.
      // We intercept this message if we detect the OnExhibition location type.
      //
      // Example: b11860777 / i1207858x
      case (None, _, _, _, NotRequestable.ItemOnHold(_), Some(LocationType.OnExhibition)) =>
        (List(AccessCondition(status = Some(AccessStatus.TemporarilyUnavailable), terms = Some("Item is on Exhibition Reserve. Please ask at the Enquiry Desk"))), ItemStatus.TemporarilyUnavailable)

      // If an item is on hold for another reader, it can't be requested -- even
      // if it would ordinarily be requestable.
      //
      // Note that an item on hold goes through two stages:
      //  1. A reader places a hold, but the item is still in the store
      //  2. A staff member collects the item from the store, and places it on the holdshelf
      //
      case (None, Some(holdCount), _, _, Requestable, Some(LocationType.ClosedStores)) if holdCount > 0=>
        (List(AccessCondition(status = Some(AccessStatus.TemporarilyUnavailable), terms = Some("Item is in use by another reader. Please ask at Enquiry Desk."))), ItemStatus.TemporarilyUnavailable)

      case (None, _, _, _, NotRequestable.ItemOnHold(_), Some(LocationType.ClosedStores)) =>
        (List(AccessCondition(status = Some(AccessStatus.TemporarilyUnavailable), terms = Some("Item is in use by another reader. Please ask at Enquiry Desk."))), ItemStatus.TemporarilyUnavailable)

      // There are some items that are labelled "bound in above" or "contained in above".
      //
      // These items aren't requestable on their own; you have to request the "primary" item.
      case (None, Some(0), _, _, NotRequestable.RequestTopItem(message), _) =>
        (List(AccessCondition(terms = Some(message))), ItemStatus.Unavailable)

      case other =>
        println(other)
        throw new RuntimeException(s"Unhandled case! $other")
    }
  }
}
