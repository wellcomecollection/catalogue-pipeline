package uk.ac.wellcome.relation_embedder

import scala.annotation.tailrec
import grizzled.slf4j.Logging

import uk.ac.wellcome.models.work.internal._
import WorkState.Identified

class ArchiveRelationsCache(works: Map[String, RelationWork])
    extends Logging {

  def apply(work: Work[Identified]): Relations =
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

  def size = relations.size

  def numParents = parentMapping.size

  private def getChildren(path: String): List[Relation] =
    childMapping
      .get(path)
      // Relations might not exist in the cache if e.g. the work is not Visible
      .getOrElse(Nil)
      .map(relations)
      .toList

  private def getSiblings(
    path: String): (List[Relation],
                    List[Relation]) = {
    val siblings = parentMapping
      .get(path)
      .map(childMapping)
      .getOrElse(Nil)
    siblings match {
      case Nil => (Nil, Nil)
      case siblings =>
        val splitIdx = siblings.indexOf(path)
        val (preceding, succeeding) = siblings.splitAt(splitIdx)
        (preceding.map(relations), succeeding.tail.map(relations))
    }
  }

  @tailrec
  private def getAncestors(path: String,
                           accum: List[Relation] = Nil)
    : List[Relation] =
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
    works
      .map {
        case (path, _) =>
          val parent = tokenize(path).dropRight(1)
          path -> works.keys.find(tokenize(_) == parent)
      }
      .collect { case (path, Some(parentPath)) => path -> parentPath }

  private lazy val childMapping: Map[String, List[String]] =
    works.map {
      case (path, _) =>
        path -> CollectionPathSorter.sortPaths(
          parentMapping.collect {
            case (childPath, parentPath) if parentPath == path =>
              childPath
          }.toList
        )
    }

  private def tokenize(path: String): List[String] =
    path.split("/").toList
}

object ArchiveRelationsCache {

  def apply(works: Seq[RelationWork]): ArchiveRelationsCache =
    new ArchiveRelationsCache(
      works
        .map { case work => work.data.collectionPath -> work }
        .collect {
          case (Some(CollectionPath(path, _)), work) =>
            path -> work
        }
        .toMap
    )
}
