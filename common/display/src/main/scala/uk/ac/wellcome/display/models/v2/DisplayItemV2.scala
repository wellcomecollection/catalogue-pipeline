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

object DisplayItemV2 extends GetIdentifiers {

  def apply(item: Item[Minted], includesIdentifiers: Boolean): DisplayItemV2 =
    item match {
      case Item(id, title, locations, _) =>
        DisplayItemV2(
          id = id.maybeCanonicalId,
          identifiers = getIdentifiers(id, includesIdentifiers),
          title = title,
          locations = locations.map(DisplayLocationV2(_))
        )
    }
}
