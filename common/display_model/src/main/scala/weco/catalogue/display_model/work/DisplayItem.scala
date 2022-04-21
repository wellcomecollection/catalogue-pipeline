package weco.catalogue.display_model.work

import io.circe.generic.extras.JsonKey
import weco.catalogue.display_model.identifiers.{
  DisplayIdentifier,
  GetIdentifiers
}
import weco.catalogue.display_model.locations.DisplayLocation
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Item

case class DisplayItem(
  id: Option[String],
  identifiers: List[DisplayIdentifier] = Nil,
  title: Option[String] = None,
  note: Option[String] = None,
  locations: List[DisplayLocation] = List(),
  status: Option[DisplayItemStatus] = None,
  @JsonKey("type") ontologyType: String = "Item"
)

object DisplayItem extends GetIdentifiers {
  def apply(item: Item[IdState.Minted]): DisplayItem =
    item match {
      case Item(id, title, note, locations) =>
        DisplayItem(
          id = id.maybeCanonicalId.map { _.underlying },
          identifiers = getIdentifiers(id),
          title = title,
          note = note,
          locations = locations.map(DisplayLocation(_)),
          status = None
        )
    }
}
