package uk.ac.wellcome.display.models.v2

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal._

@Schema(
  name = "Item",
  description = "An item is a manifestation of a Work."
)
case class DisplayItemV2(
  @Schema(
    `type` = "String",
    readOnly = true,
    description = "The canonical identifier given to a thing.") id: Option[
    String],
  @Schema(
    `type` = "List[uk.ac.wellcome.display.models.v2.DisplayIdentifierV2]",
    description =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifierV2]] = None,
  @Schema(
    readOnly = true,
    description = "A human readable title."
  ) title: Option[String] = None,
  @Schema(
    description = "List of locations that provide access to the item"
  ) locations: List[DisplayLocationV2] = List(),
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Item"
)

object DisplayItemV2 {

  def apply(item: Minted[Item],
            includesIdentifiers: Boolean): DisplayItemV2 =
    item match {
      case identifiedItem: Identified[Item] =>
        DisplayItemV2(
          id = Some(identifiedItem.canonicalId),
          identifiers =
            if (includesIdentifiers)
              Some(identifiedItem.identifiers.map { DisplayIdentifierV2(_) })
            else None,
          title = identifiedItem.agent.title,
          locations = displayLocations(identifiedItem.agent)
        )
      case unidentifiableItem: Unidentifiable[Item] =>
        DisplayItemV2(
          id = None,
          identifiers = None,
          title = unidentifiableItem.agent.title,
          locations = displayLocations(unidentifiableItem.agent)
        )
    }

  private def displayLocations(item: Item) =
    item.locations.map(DisplayLocationV2(_))
}
