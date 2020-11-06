package uk.ac.wellcome.relation_embedder

import scala.annotation.tailrec

import uk.ac.wellcome.models.work.internal._
import WorkState.Merged

class ArchiveRelationsCache(works: Map[String, Work[Merged]]) {

  def relations(work: Work[Merged]): Relations[DataState.Unidentified] =
    work
      .data
      .collectionPath
      .map { case CollectionPath(path, _, _) =>
        val ancestorWorks = ancestors(path)
        val (siblingsPrecedingWorks, siblingsSucceedingWorks) = siblings(path)
        val depth = ancestorWorks.length
        Relations(
          ancestors = ancestorWorks.zipWithIndex.map {
            case (work, i) => Relation(work, i)
          },
          children = children(path).map(Relation(_, depth + 1)),
          siblingsPreceding = siblingsPrecedingWorks.map(Relation(_, depth)),
          siblingsSucceeding = siblingsSucceedingWorks.map(Relation(_, depth))
        )
      }
      .getOrElse(Relations.none)

  private def children(path: String): List[Work[Merged]] =
    childMapping(path).map(works).toList

  private def siblings(path: String): (List[Work[Merged]], List[Work[Merged]]) = {
    val siblings = parentMapping
      .get(path)
      .map(childMapping)
      .map(siblingPaths => siblingPaths.map(works).toList)
      .getOrElse(Nil)
    siblings match {
      case Nil => (Nil, Nil)
      case siblings =>
        val (siblingsPreceding, siblingsSucceeding) = siblings
          .splitAt(siblings.indexOf(path))
        (siblingsPreceding, siblingsSucceeding.tail)
    }
  }

  @tailrec
  private def ancestors(path: String,
                        accum: List[Work[Merged]] = Nil): List[Work[Merged]] =
    parentMapping.get(path) match {
      case None => accum
      case Some(parentPath) => ancestors(path, works(parentPath) :: accum)
    }

  private lazy val parentMapping: Map[String, String] =
    works
      .map { case (path, _) =>
        val pathTokens = tokenize(path)
        path -> works
          .keys
          .find { parentPath =>
            val parentPathTokens = tokenize(parentPath)
            pathTokens == parentPathTokens.slice(0, parentPathTokens.length - 1)
          }
      }
      .collect { case (path, Some(parentPath)) => path -> parentPath }

  private lazy val childMapping: Map[String, List[String]] =
    works.map { case (path, work) =>
      path -> CollectionPathSorter.sortPaths(
        parentMapping
          .collect {
            case (childPath, parentPath) if parentPath == path =>
              childPath
          }
          .toList
        )
    }

  private def tokenize(path: String): List[String] =
    path.split("/").toList
}

object ArchiveRelationsCache {

  def apply(works: List[Work[Merged]]): ArchiveRelationsCache =
    new ArchiveRelationsCache(
      works
        .map { case work => work.data.collectionPath -> work }
        .collect { case (Some(CollectionPath(path, _, _)), work) =>
          path -> work
        }
        .toMap
    )
}
