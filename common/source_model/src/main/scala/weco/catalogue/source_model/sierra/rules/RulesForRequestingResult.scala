package weco.catalogue.source_model.sierra.rules

sealed trait RulesForRequestingResult

case object Requestable extends RulesForRequestingResult

sealed trait NotRequestable extends RulesForRequestingResult

object NotRequestable {
  case class NeedsManualRequest(message: String) extends NotRequestable

  case class ItemClosed(message: String) extends NotRequestable
  case class ItemMissing(message: String) extends NotRequestable
  case class ItemOnSearch(message: String) extends NotRequestable
  case class ItemUnavailable(message: String) extends NotRequestable
  case class ItemWithdrawn(message: String) extends NotRequestable

  case class ContactUs(message: String) extends NotRequestable

  case class OnHold(message: String) extends NotRequestable

  case class OnOpenShelves(message: String) extends NotRequestable
  case class OnExhibition(message: String) extends NotRequestable
  case class OnNewBooksDisplay(message: String) extends NotRequestable

  case class AtDigitisation(message: String) extends NotRequestable
  case class AtConservation(message: String) extends NotRequestable

  case class RequestTopItem(message: String) extends NotRequestable

  case class BelongsInStrongroom(message: String) extends NotRequestable

  case object NoReason extends NotRequestable
}
