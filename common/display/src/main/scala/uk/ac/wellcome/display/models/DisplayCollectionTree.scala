package uk.ac.wellcome.display.models

import io.swagger.v3.oas.annotations.media.Schema

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.display.models.v2.DisplayWorkV2

@Schema(
  name = "CollectionTree",
  description = "A hierarchical collection of works."
)
case class DisplayCollectionTree(
  @Schema(
    description =
      "A slash separated path containing the position within the hierarchy."
  ) path: String,
  @Schema(
    description =
      "The level of the node. Either Collection, Section, Series, SubSeries or Item"
  ) level: String,
  @Schema(
    description =
      "The work. This only contains a limited set of fields, regardless of the includes."
  ) work: DisplayWorkV2,
  @Schema(
    description = "An optional label for the node."
  ) label: Option[String] = None,
  @Schema(
    description =
      "An array containing any children. This value is null when a given node has not been expanded."
  ) children: Option[List[DisplayCollectionTree]] = None,
)

object DisplayCollectionTree {

  def apply(tree: CollectionTree,
            expandedPaths: List[String]): DisplayCollectionTree =
    DisplayCollectionTree(
      path = tree.path,
      level = tree.level match {
        case CollectionLevel.Collection => "Collection"
        case CollectionLevel.Section    => "Section"
        case CollectionLevel.Series     => "Series"
        case CollectionLevel.Item       => "Item"
      },
      work = DisplayWorkV2(tree.work),
      label = tree.label,
      children =
        if (isExpanded(tree.path, expandedPaths))
          Some(tree.children.map(DisplayCollectionTree(_, expandedPaths)))
        else
          None
    )

  private def isExpanded(path: String, expandedPaths: List[String]): Boolean =
    expandedPaths.exists(_.startsWith(path))
}
