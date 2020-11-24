package uk.ac.wellcome.relation_embedder

import scala.annotation.tailrec
import grizzled.slf4j.Logging

import uk.ac.wellcome.models.work.internal._
import WorkState.Merged

class ArchiveRelationsCache(
  relations: Map[String, Relation[DataState.Unidentified]])
    extends Logging {

  def apply(work: Work[Merged]): Relations[DataState.Unidentified] =
    work.data.collectionPath
      .map {
        case CollectionPath(path, _, _) =>
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
            info(
              s"Found no relations for work with path $path")
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

  private def getChildren(
    path: String): List[Relation[DataState.Unidentified]] =
    childMapping
      .get(path)
      // Relations might not exist in the cache if e.g. the work is not Visible
      .getOrElse(Nil)
      .map(relations)
      .toList

  private def getSiblings(
    path: String): (List[Relation[DataState.Unidentified]],
                    List[Relation[DataState.Unidentified]]) = {
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
                           accum: List[Relation[DataState.Unidentified]] = Nil)
    : List[Relation[DataState.Unidentified]] =
    parentMapping.get(path) match {
      case None => accum
      case Some(parentPath) =>
        getAncestors(parentPath, relations(parentPath) :: accum)
    }

  private lazy val parentMapping: Map[String, String] =
    relations
      .map {
        case (path, _) =>
          val parent = tokenize(path).dropRight(1)
          path -> relations.keys.find(tokenize(_) == parent)
      }
      .collect { case (path, Some(parentPath)) => path -> parentPath }

  private lazy val childMapping: Map[String, List[String]] =
    relations.map {
      case (path, work) =>
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

  def apply(works: Seq[Work[Merged]]): ArchiveRelationsCache =
    new ArchiveRelationsCache(
      works
        .map { case work => work.data.collectionPath -> work }
        .collect {
          case (Some(CollectionPath(path, _, _)), work) =>
            path -> Relation(work, path.split("/").length - 1)
        }
        .toMap
    )
}
