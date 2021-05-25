package uk.ac.wellcome.platform.transformer.sierra.transformers

import weco.catalogue.internal_model.locations.{AccessCondition, LocationType, PhysicalLocationType}
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
  object OpacMsg {
    val OnlineRequest = "f"
    val OpenShelves = "o"
  }

  case object Status {
    val Available = "-"
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

//    val maybeDisplayNote = if (displayNote.isEmpty) None else Some(displayNote)

    (bibAccessStatus, holdCount, status, opacmsg, isRequestable, location) match {

      // Items on the open shelves don't have any access conditions.
      //
      // We could add an access status of "Open" here, but it feels dubious to be
      // synthesising access information that doesn't come from the source records.
      //
      // TODO: Do any items on the open shelves have a display note we want to expose?
      case (None, Some(0), Some(Status.Available), Some(OpacMsg.OpenShelves), NotRequestable.OpenShelves(_), Some(LocationType.OpenShelves)) =>
        if (displayNote.nonEmpty) {
          println(s"Warn: $itemId is open shelves/available but has a display note $displayNote")
        }
        (List(), ItemStatus.Available)

      // Items on the closed stores that are requestable get the "Online request" condition.
      //
      // TODO: Do any of these items have a display note we want to expose?
      case (None, Some(0), Some(Status.Available), Some(OpacMsg.OnlineRequest), Requestable, Some(LocationType.ClosedStores)) =>
        if (displayNote.nonEmpty) {
          println(s"Warn: $itemId is open shelves/available but has a display note $displayNote")
        }
        (List(AccessCondition(terms = Some("Online request"))), ItemStatus.Available)

      case other =>
        println(other)
        throw new RuntimeException(s"Unhandled case! $other")
    }
  }
}
