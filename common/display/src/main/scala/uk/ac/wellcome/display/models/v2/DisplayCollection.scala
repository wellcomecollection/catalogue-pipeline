package uk.ac.wellcome.display.models.v2

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal.Collection

@Schema(
  name = "DisplayCollection",
  description = "A collection that a work is part of"
)
case class DisplayCollection(
  @Schema(description = "The label of the collection") label: Option[String],
  @Schema(description = "Where in the hierarchy a work is in the collection") path: String,
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Collection"
)

object DisplayCollection {
  def apply(collection: Collection): DisplayCollection =
    DisplayCollection(
      label = collection.label,
      path = collection.path
    )
}
