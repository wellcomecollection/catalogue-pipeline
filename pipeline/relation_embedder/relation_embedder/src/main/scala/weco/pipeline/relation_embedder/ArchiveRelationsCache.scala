package weco.pipeline.relation_embedder

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.catalogue.internal_model.work._

import scala.annotation.tailrec

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

  // The availabilities of an archive's Relations are the union of
  // all of its descendants' availabilities, as well as its own
  def getAvailabilities(work: Work[Merged]): Set[Availability] =
    work.data.collectionPath
      .map {
        case CollectionPath(path, _) =>
          @tailrec
          def availabilities(stack: List[String],
                             accum: Set[Availability]): Set[Availability] =
            stack match {
              case Nil => accum
              case head :: tail =>
                val children = childMapping.getOrElse(head, Nil)

                val childAvailabilities =
                  (head :: children)
                    .map(works)
                    .map(_.state.availabilities)
                    .foldLeft(Set.empty[Availability])(_ union _)

                availabilities(
                  childMapping.getOrElse(head, Nil) ++ tail,
                  accum union childAvailabilities
                )
            }
          availabilities(
            childMapping.getOrElse(path, Nil),
            works.get(path).map(_.state.availabilities).getOrElse(Set.empty)
          )
      }
      .getOrElse(Set.empty)

  def size = relations.size

  def numParents = parentMapping.size

  private def getChildren(path: String): List[Relation] =
    childMapping
    // Relations might not exist in the cache if e.g. the work is not Visible
      .getOrElse(path, default = Nil)
      .map(relations)

  import weco.pipeline.relation_embedder.models.PathOps._

  private def getSiblings(path: String): (List[Relation], List[Relation]) = {
    val (preceding, succeeding) = works.keySet.siblingsOf(path)

    (preceding.map(relations), succeeding.map(relations))
  }

  @tailrec
  private def getAncestors(path: String,
                           accum: List[Relation] = Nil): List[Relation] =
    parentMapping.get(path) match {
      case None => accum
      case Some(parentPath) =>
        getAncestors(parentPath, relations(parentPath) :: accum)
    }

  private def getNumChildren(path: String): Int =
    childMapping.get(path).map(_.length).getOrElse(0)

  private def getNumDescendents(path: String): Int = {
    @tailrec
    def numDescendents(stack: List[String], accum: Int = 0): Int =
      stack match {
        case Nil => accum
        case head :: tail =>
          numDescendents(childMapping.getOrElse(head, Nil) ++ tail, 1 + accum)
      }
    numDescendents(childMapping.getOrElse(path, Nil))
  }

  private lazy val relations: Map[String, Relation] =
    works.map {
      case (path, work) =>
        path -> work.toRelation(
          depth = path.split("/").length - 1,
          numChildren = getNumChildren(path),
          numDescendents = getNumDescendents(path)
        )
    }

  private lazy val parentMapping: Map[String, String] =
    works.keySet.parentMapping

  private lazy val childMapping: Map[String, List[String]] =
    works.keySet.childMapping
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
