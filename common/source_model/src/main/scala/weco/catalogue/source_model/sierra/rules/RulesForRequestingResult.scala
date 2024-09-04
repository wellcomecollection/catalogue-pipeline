package weco.catalogue.source_model.sierra.rules

sealed trait RulesForRequestingResult

case object Requestable extends RulesForRequestingResult

sealed trait NotRequestable extends RulesForRequestingResult

object NotRequestable {
  val defaultManualRequestMessage =
    "This item cannot be requested online. Please place a manual request."
  val defaultItemUnavailableMessage =
    "This item is unavailable."

  case class NeedsManualRequest(message: String = defaultManualRequestMessage) extends NotRequestable

  case class ItemClosed(message: String) extends NotRequestable
  case class SafeguardedItem(message: String) extends NotRequestable
  case class ItemMissing(message: String) extends NotRequestable
  case class ItemOnSearch(message: String) extends NotRequestable
  case class ItemUnavailable(message: String = defaultItemUnavailableMessage) extends NotRequestable
  case class ItemWithdrawn(message: String) extends NotRequestable

  case class ContactUs(message: String) extends NotRequestable

  case class InUseByAnotherReader(message: String) extends NotRequestable

  case class OnOpenShelves(message: String) extends NotRequestable
  case class OnExhibition(message: String) extends NotRequestable
  case class OnNewBooksDisplay(message: String) extends NotRequestable

  case class AtDigitisation(message: String) extends NotRequestable
  case class AtConservation(message: String) extends NotRequestable

  case class RequestTopItem(message: String) extends NotRequestable

  // This is used for items that are prevented from requesting, and the rule
  // doesn't include a message to display to users.
  //
  // This message is only meant for internal debugging to help people looking
  // at application logs and understand which particular rule was used, and
  // should not be displayed publicly.
  case class NoPublicMessage(internalMessage: String) extends NotRequestable
}
