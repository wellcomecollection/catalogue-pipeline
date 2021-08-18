package weco.pipeline.relation_embedder

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
              s"Found relations for work with path $path: ${ancestors.size} ancestors, ${children.size} children, and ${siblingsPreceding.size + siblingsSucceeding.size} siblings")
          relations
      }
      .getOrElse {
        warn(s"Received work with empty collectionPath field: ${work.id}")
        Relations.none
      }

  import weco.pipeline.relation_embedder.models.PathOps._

  private val paths: Set[String] = works.keySet

  // The availabilities of an archive's Relations are the union of
  // all of its descendants' availabilities, as well as its own
  def getAvailabilities(work: Work[Merged]): Set[Availability] =
    work.data.collectionPath match {
      case Some(CollectionPath(workPath, _)) =>
        works
          .filter {
            case (path, _) => path == workPath || path.isDescendentOf(workPath)
          }
          .flatMap { case (_, work) => work.state.availabilities }
          .toSet

      // We shouldn't be dealing with any works without a collectionPath field in the
      // relation embedder; if we are then something has gone wrong.
      case _ =>
        assert(
          assertion = false,
          message = s"Cannot get availabilities for work with empty collectionPath field: $work"
        )
        Set()
    }

  def size = relations.size

  def numParents = parentMapping.size

  private def getChildren(path: String): List[Relation] =
    paths.childrenOf(path).map(relations)

  private def getSiblings(path: String): (List[Relation], List[Relation]) = {
    val (preceding, succeeding) = paths.siblingsOf(path)

    (preceding.map(relations), succeeding.map(relations))
  }

  private def getAncestors(path: String): List[Relation] =
    paths.knownAncestorsOf(path).map(relations)

  private lazy val relations: Map[String, Relation] =
    works.map {
      case (path, work) =>
        path -> work.toRelation(
          depth = path.split("/").length - 1,
          numChildren = paths.childrenOf(path).length,
          numDescendents = paths.descendentsOf(path).length
        )
    }

  private lazy val parentMapping: Map[String, String] =
    works.keySet.parentMapping
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
