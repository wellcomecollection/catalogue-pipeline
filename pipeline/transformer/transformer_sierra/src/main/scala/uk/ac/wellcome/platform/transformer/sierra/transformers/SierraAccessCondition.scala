package uk.ac.wellcome.platform.transformer.sierra.transformers

import weco.catalogue.internal_model.locations.{AccessCondition, AccessStatus, LocationType, PhysicalLocationType}
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
  }

  object OpacMsg {
    val OnlineRequest = "f"
    val ManualRequest = "n"
    val OpenShelves = "o"
    val ByAppointment = "a"
    val AtDigitisation = "b"
  }

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

      // Items on the open shelves don't have any access conditions.
      //
      // We could add an access status of "Open" here, but it feels dubious to be
      // synthesising access information that doesn't come from the source records.
      //
      // TODO: Do any items on the open shelves have a display note we want to expose?
      //
      // Example: b1659504x / i15894897
      case (None, Some(0), Some(Status.Available), Some(OpacMsg.OpenShelves), NotRequestable.OpenShelves(_), Some(LocationType.OpenShelves)) =>
        if (displayNote.nonEmpty) {
          println(s"Warn: $bibId / $itemId is open shelves/available but has a display note $displayNote")
        }
        (List(), ItemStatus.Available)

      // Items on the closed stores that are requestable get the "Online request" condition.
      //
      // TODO: Do any of these items have a display note we want to expose?
      //
      // Example: b18799966 / i17571170
      case (None, Some(0), Some(Status.Available), Some(OpacMsg.OnlineRequest), Requestable, Some(LocationType.ClosedStores)) =>
        if (displayNote.nonEmpty) {
          println(s"Warn: $bibId / $itemId is online request but has a display note $displayNote")
        }
        (List(AccessCondition(terms = Some("Online request"))), ItemStatus.Available)

      // The status "by appointment" takes precedence over "permission required".
      //
      // Example: b32214832 / i19389383
      case (Some(AccessStatus.ByAppointment), Some(0), Some(Status.PermissionRequired), Some(OpacMsg.ByAppointment), NotRequestable.PermissionRequired, Some(LocationType.ClosedStores)) =>
        (List(AccessCondition(status = Some(AccessStatus.ByAppointment), note = maybeDisplayNote)), ItemStatus.Available)

      // Manual requesting if all values are consistent.
      //
      // Example: b32214832 / i19389383
      case (None, Some(0), Some(Status.Available), Some(OpacMsg.ManualRequest), NotRequestable.ManualRequest(message), Some(LocationType.ClosedStores)) =>
        if (displayNote.nonEmpty) {
          println(s"Warn: $bibId / $itemId is manual request but has a display note $displayNote")
        }
        (List(AccessCondition(terms = Some(message))), ItemStatus.Available)

      // A missing status overrides all other values.
      //
      // Example: b10379198 / i10443861
      case (_, _, Some(Status.Missing), _, NotRequestable.ItemMissing(message), _) =>
        if (displayNote.nonEmpty) {
          println(s"Warn: $bibId / $itemId is missing but has a display note $displayNote")
        }
        (List(AccessCondition(status = Some(AccessStatus.Unavailable), terms = Some(message))), ItemStatus.Unavailable)

      // If an item is at digitisation, then it's temporarily unavailable -- we expect
      // it will come back to the stores at some point.
      //
      // Example: b14465978 / i13753228
      case (_, _, Some(Status.Unavailable), Some(OpacMsg.AtDigitisation), _: NotRequestable, Some(LocationType.ClosedStores)) =>
        if (displayNote.nonEmpty) {
          println(s"Warn: $bibId / $itemId is missing but has a display note $displayNote")
        }
        (List(AccessCondition(status = Some(AccessStatus.TemporarilyUnavailable), terms = Some("At digitisation and temporarily unavailable."))), ItemStatus.TemporarilyUnavailable)

      case other =>
        println(other)
        throw new RuntimeException(s"Unhandled case! $other")
    }
  }
}
