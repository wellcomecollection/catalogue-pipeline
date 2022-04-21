package weco.pipeline.relation_embedder.models

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.catalogue.internal_model.work._

class ArchiveRelationsCache(works: Map[String, RelationWork]) extends Logging {

  def apply(work: Work[Merged]): Relations =
    work.data.collectionPath
      .map {
        case CollectionPath(path, _) =>
          val (siblingsPreceding, siblingsSucceeding) = getSiblings(path)
          val ancestors = getAncestors(path)
          val children = getChildren(path)
          val relations = Relations(
            ancestors = ancestors,
            children = children,
            siblingsPreceding = siblingsPreceding,
            siblingsSucceeding = siblingsSucceeding
          )
          if (relations == Relations.none)
            info(s"Found no relations for work with path $path")
          else
            info(
              s"Found relations for work with path $path: ${ancestors.size} ancestors, ${children.size} children, and ${siblingsPreceding.size + siblingsSucceeding.size} siblings"
            )
          relations
      }
      .getOrElse {
        warn(s"Received work with empty collectionPath field: ${work.id}")
        Relations.none
      }

  private val paths: PathCollection = PathCollection(works.keySet)

  def size: Int = works.size

  def numParents: Int = paths.parentMapping.size

  private def getChildren(path: String): List[Relation] =
    paths.childrenOf(path).map(relations)

  private def getSiblings(path: String): (List[Relation], List[Relation]) = {
    val (preceding, succeeding) = paths.siblingsOf(path)

    (preceding.map(relations), succeeding.map(relations))
  }

  private def getAncestors(path: String): List[Relation] =
    paths.knownAncestorsOf(path).map(relations)

  import weco.pipeline.relation_embedder.models.PathOps._

  private lazy val relations: Map[String, Relation] =
    works.map {
      case (path, work) =>
        path -> work.toRelation(
          depth = path.depth,
          numChildren = paths.childrenOf(path).length,
          numDescendents = paths.knownDescendentsOf(path).length
        )
    }
}

object ArchiveRelationsCache {

  def apply(works: Seq[RelationWork]): ArchiveRelationsCache =
    new ArchiveRelationsCache(
      works
        .map { work =>
          work.data.collectionPath -> work
        }
        .collect {
          case (Some(CollectionPath(path, _)), work) =>
            path -> work
        }
        .toMap
    )
}
