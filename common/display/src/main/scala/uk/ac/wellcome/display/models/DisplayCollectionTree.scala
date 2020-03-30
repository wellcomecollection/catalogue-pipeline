package uk.ac.wellcome.display.models

import io.swagger.v3.oas.annotations.media.Schema

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.display.models.v2._

@Schema(
  name = "CollectionTree",
  description = "A hierarchical collection of works."
)
case class DisplayCollectionTree(
  @Schema(
    description = "A path containing the position within the hierarchy."
  ) path: DisplayCollectionPath,
  @Schema(
    description =
      "The work. This only contains a limited set of fields, regardless of the includes."
  ) work: DisplayWorkV2,
  @Schema(
    description =
      "An array containing any children. This value is null when a given node has not been expanded."
  ) children: Option[List[DisplayCollectionTree]] = None,
)

object DisplayCollectionTree {

  def apply(tree: CollectionTree,
            expandedPaths: List[String]): DisplayCollectionTree =
    DisplayCollectionTree(
      path = DisplayCollectionPath(tree.path),
      work = DisplayWorkV2(tree.work),
      children =
        if (isExpanded(tree.path, expandedPaths))
          Some(tree.children.map(DisplayCollectionTree(_, expandedPaths)))
        else
          None
    )

  private def isExpanded(path: CollectionPath,
                         expandedPaths: List[String]): Boolean =
    expandedPaths.exists(_.startsWith(path.path))
}
