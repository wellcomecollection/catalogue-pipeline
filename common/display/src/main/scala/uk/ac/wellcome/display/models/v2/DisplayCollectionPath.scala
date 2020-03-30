package uk.ac.wellcome.display.models.v2

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal._

@Schema(
  name = "Collection",
  description = "A collection that a work is part of"
)
case class DisplayCollectionPath(
  @Schema(
    description = "Where in the hierarchy a work is in the collection"
  ) path: String,
  @Schema(
    description =
      "The level of the node. Either Collection, Section, Series, SubSeries or Item"
  ) level: String,
  @Schema(
    description = "The label of the collection"
  ) label: Option[String] = None,
  @JsonKey("type") @Schema(name = "type") ontologyType: String =
    "CollectionPath"
)

object DisplayCollectionPath {
  def apply(collectionPath: CollectionPath): DisplayCollectionPath =
    DisplayCollectionPath(
      path = collectionPath.path,
      level = DisplayCollectionLevel(collectionPath.level),
      label = collectionPath.label,
    )
}

object DisplayCollectionLevel {

  def apply(level: CollectionLevel): String =
    level match {
      case CollectionLevel.Collection => "Collection"
      case CollectionLevel.Section    => "Section"
      case CollectionLevel.Series     => "Series"
      case CollectionLevel.Item       => "Item"
    }
}
