package uk.ac.wellcome.display.models.v2

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema

import uk.ac.wellcome.models.work.internal._

@Schema(
  name = "Collection",
  description = "A hierarchical collection of works."
)
case class DisplayCollection(
  @Schema(
    description = "A path containing the position within the hierarchy."
  ) path: DisplayCollectionPath,
  @Schema(
    description =
      "The work. This only contains a limited set of fields, regardless of the includes."
  ) work: Option[DisplayWorkV2] = None,
  @Schema(
    description =
      "An array containing any children. This value is null when a given node has not been expanded."
  ) children: Option[List[DisplayCollection]] = None,
)

object DisplayCollection {

  def apply(tree: Collection, expandedPaths: List[String]): DisplayCollection =
    DisplayCollection(
      path = DisplayCollectionPath(tree.path),
      work = tree.work.map(DisplayWorkV2(_)),
      children =
        if (isExpanded(tree.path, expandedPaths))
          Some(tree.children.map(DisplayCollection(_, expandedPaths)))
        else
          None
    )

  private def isExpanded(path: CollectionPath,
                         expandedPaths: List[String]): Boolean =
    expandedPaths.exists(_.startsWith(path.path))
}

@Schema(
  name = "CollectionPath",
  description =
    "Where a particular work is located within a hierarchical collection"
)
case class DisplayCollectionPath(
  @Schema(
    description = "Where in the hierarchy a work is in the collection"
  ) path: String,
  @Schema(
    description = "The level of the node."
  ) level: Option[String] = None,
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
      level = collectionPath.level.map(DisplayCollectionLevel(_)),
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
