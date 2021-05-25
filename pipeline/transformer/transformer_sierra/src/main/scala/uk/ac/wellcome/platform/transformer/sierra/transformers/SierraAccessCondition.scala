package uk.ac.wellcome.platform.transformer.sierra.transformers

import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessStatus,
  LocationType,
  PhysicalLocationType
}
import weco.catalogue.source_model.sierra.marc.VarField
import weco.catalogue.source_model.sierra.source.SierraQueryOps
import weco.catalogue.source_model.sierra.{
  NotRequestable,
  OtherNotRequestable,
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
    val displayNote = itemData.varFields.filter { _.fieldTag.contains("n") }.flatMap {
      case VarField(Some(content), _, _, _, _, Nil) => List(content)
      case VarField(None, _, _, _, _, subfields) =>
        subfields.withTag("a").map { _.content }
      case _ => throw new Throwable("???")
    }.mkString(" ")

    val maybeDisplayNote = if (displayNote.isEmpty) None else Some(displayNote)

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
          List(AccessCondition(status = Some(AccessStatus.Open), terms = Some("Online request"), note = maybeDisplayNote)),
          ItemStatus.Available
        )

      // - = "available"
      // f = "Online request"
      case (None, Some(holdCount), Some("-"), Some("f"), Requestable, Some(LocationType.ClosedStores)) if holdCount > 0 =>
        (
          List(AccessCondition(status = Some(AccessStatus.TemporarilyUnavailable), terms = Some("Item is on hold for another reader."), note = maybeDisplayNote)),
          ItemStatus.TemporarilyUnavailable
        )
      case (None, _, _, Some("f"), NotRequestable.ItemOnHold(message), Some(LocationType.ClosedStores)) =>
        (
          List(AccessCondition(status = Some(AccessStatus.TemporarilyUnavailable), terms = Some(message), note = maybeDisplayNote)),
          ItemStatus.TemporarilyUnavailable
        )

      // "b" / "c" = "as above"
      case (_, _, Some("b"), _, _, _) | (_, _, Some("c"), _, _, _) =>
        (List(), ItemStatus.Unavailable)

      // "y" = "permission required"
      // "a" = "by appointment"
      case (Some(AccessStatus.ByAppointment), _, Some("y"), Some("a"), NotRequestable.PermissionRequired, Some(LocationType.ClosedStores)) |
           (None, _, Some("y"), Some("a"), NotRequestable.PermissionRequired, Some(LocationType.ClosedStores)) =>
        (
          List(AccessCondition(status = Some(AccessStatus.ByAppointment), note = maybeDisplayNote)),
          ItemStatus.Available
        )

      // "-" = "available"
      // "n" = "manual request"
      case (None, _, Some("-"), Some("n"), notRequestable: NotRequestable, Some(LocationType.ClosedStores)) =>
        (
          List(
            AccessCondition(
              terms = Some("Manual request"),
              note = Seq(maybeDisplayNote, notRequestable.message).flatten.headOption
            )
          ),
          ItemStatus.Unavailable
        )

      case (_, _, _, _, NotRequestable.ItemMissing(missingMessage), _) =>
        (
          List(
            AccessCondition(
              status = Some(AccessStatus.Unavailable),
              terms = Some(missingMessage),
              note = maybeDisplayNote
            )
          ),
          ItemStatus.Unavailable
        )

      case (_, _, _, _, NotRequestable.ItemWithdrawn(withdrawnMessage), _) =>
        (
          List(
            AccessCondition(
              status = Some(AccessStatus.Unavailable),
              terms = Some(withdrawnMessage),
              note = maybeDisplayNote
            )
          ),
          ItemStatus.Unavailable
        )

      case (_, _, _, _, NotRequestable.ItemUnavailable(unavailableMessage), _) =>
        (
          List(
            AccessCondition(
              status = Some(AccessStatus.Unavailable),
              terms = Some(unavailableMessage),
              note = maybeDisplayNote
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
              terms = Some("At digitisation and temporarily unavailable."),
              note = maybeDisplayNote
            )
          ),
          ItemStatus.TemporarilyUnavailable
        )

      // "y" = "permission required"
      // "q" = "donor permission"
      case (Some(AccessStatus.PermissionRequired), _, Some("y"), Some("q"), NotRequestable.PermissionRequired, Some(LocationType.ClosedStores)) =>
        (
          List(
            AccessCondition(status = Some(AccessStatus.PermissionRequired), note = maybeDisplayNote)
          ),
          ItemStatus.Available
        )

      // "h" = "closed"
      case (Some(AccessStatus.Closed), _, _, _, NotRequestable.ItemClosed(_), Some(LocationType.ClosedStores)) |
           (None, _, _, _, NotRequestable.ItemClosed(_), Some(LocationType.ClosedStores)) |
           (Some(AccessStatus.Closed), _, _, _, NotRequestable.ItemClosed(_), None) =>
        (
          List(
            AccessCondition(status = Some(AccessStatus.Closed), note = maybeDisplayNote)
          ),
          ItemStatus.Unavailable
        )

      // "6" = "restricted"
      // "f" = "Online request"
      case (Some(AccessStatus.Restricted), Some(0), status, Some("f"), Requestable, Some(LocationType.ClosedStores)) if status.contains("6") | status.contains("-") =>
        (
          List(
            AccessCondition(status = Some(AccessStatus.Restricted), terms = Some("Online request"), note = maybeDisplayNote)
          ),
          ItemStatus.Available
        )

      // "s" = "staff use only"
      case (_, _, _, Some("s"), _: NotRequestable, Some(LocationType.ClosedStores)) =>
        (
          List(
            AccessCondition(status = Some(AccessStatus.Unavailable), terms = Some("Staff use only"), note = maybeDisplayNote)
          ),
          ItemStatus.Unavailable
        )

      // "a" = "by appointment
      case (_, _, _, Some("a"), OtherNotRequestable(None), Some(LocationType.ClosedStores)) =>
        (
          List(
            AccessCondition(status = AccessStatus.ByAppointment)
          ),
          ItemStatus.Unavailable
        )

      // This is more conservative than Encore, which will allow you to request such items.
      // I'm guessing that Encore is wrong here, and suppressing requests is the right approach.
      case (Some(AccessStatus.TemporarilyUnavailable), _, Some("-"), Some("f"), Requestable, Some(LocationType.ClosedStores)) =>
        (
          List(
            AccessCondition(status = AccessStatus.TemporarilyUnavailable)
          ),
          ItemStatus.TemporarilyUnavailable
        )

      case (None, _, Some("-"), Some("i"), NotRequestable.ManualRequest(message), Some(LocationType.ClosedStores)) =>
        (
          List(
            AccessCondition(
              terms = Some("Manual request"),
              note = maybeDisplayNote match {
                case Some(note) => Some(note)
                case None => Some(message)
              }
            )
          ),
          ItemStatus.Available
        )

      // This sometimes happens where the bib-level record has been updated, but the item
      // not.  There's often an accompanying bib message like:
      //
      //    This item was previously restricted until 01/01/2021. It was reassessed in April 2021
      //    and subsequently opened
      //
      // but there's no way to distinguish that from:
      //
      //    This item was previously open until 01/01/2021. It was reassessed in April 2021 and
      //    subsequently restricted
      //
      // so we assume the least permissive status.
      //
      // "6" = "Restricted"
      // "f" = "Online request"
      //
      // TODO: Log here
      case (Some(AccessStatus.Open), Some(0), Some("6"), Some("f"), Requestable, Some(LocationType.ClosedStores)) =>
        (
          List(
            AccessCondition(
              status = Some(AccessStatus.Restricted),
              terms = Some("Online request"),
              note = maybeDisplayNote
            )
          ),
          ItemStatus.Available
        )

      case (Some(AccessStatus.TemporarilyUnavailable), _, Some("r"), Some("u"), OtherNotRequestable(message), Some(LocationType.ClosedStores)) =>
        (
          List(
            AccessCondition(
              status = Some(AccessStatus.TemporarilyUnavailable),
              terms = maybeDisplayNote match {
                case Some(note) => Some(note)
                case None => message
              },
            )
          ),
          ItemStatus.TemporarilyUnavailable
        )

      // This looks like a flat-out bug in the data.  Only the OPACMSG tells us this is a manual request.
      // "n" = "manual request"
      // As above, we choose the msot conservative case.
      case (None, Some(0), Some("-"), Some("n"), Requestable, Some(LocationType.ClosedStores)) =>
        (
          List(
            AccessCondition(
              terms = Some("Manual request"),
              note = maybeDisplayNote
            )
          ),
          ItemStatus.Available
        )

      // Another data bug.  Only the bib tells us this isn't requestable online.
      // As above, choose conservatively, double-check later.
      // "-" = "available"
      // "f" = "online request"
      case (Some(AccessStatus.ByAppointment), Some(0), Some("-"), Some("f"), Requestable, Some(LocationType.ClosedStores)) =>
        (
          List(
            AccessCondition(status = AccessStatus.ByAppointment)
          ),
          ItemStatus.Available
        )

      // Looks like a bug somewhere
      // an open shelves item shouldn't be closed stores
      case (None, Some(0), Some("-"), Some("i"), NotRequestable.OpenShelves(_), Some(LocationType.ClosedStores)) =>
        (
          List(
            AccessCondition(terms = Some("Ask at desk"))
          ),
          ItemStatus.Available
        )

      case other =>
        println(other)
        throw new RuntimeException(s"Unhandled case! $other")
    }
  }
}
