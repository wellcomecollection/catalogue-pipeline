package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal._

@Schema(
  name = "Item",
  description = "An item is a manifestation of a Work."
)
case class DisplayItem(
  @Schema(
    `type` = "String",
    readOnly = true,
    description = "The canonical identifier given to a thing.") id: Option[
    String],
  @Schema(
    `type` = "List[uk.ac.wellcome.Display.models.DisplayIdentifier]",
    description =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifier]] = None,
  @Schema(
    readOnly = true,
    description = "A human readable title."
  ) title: Option[String] = None,
  @Schema(
    description = "List of locations that provide access to the item"
  ) locations: List[DisplayLocationDeprecated] = List(),
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Item"
)

object DisplayItem extends GetIdentifiers {

  def apply(item: Item[IdState.Minted],
            includesIdentifiers: Boolean): DisplayItem =
    item match {
      case Item(id, title, locations, _) =>
        DisplayItem(
          id = id.maybeCanonicalId,
          identifiers = getIdentifiers(id, includesIdentifiers),
          title = title,
          locations = locations.map(DisplayLocationDeprecated(_))
        )
    }
}
