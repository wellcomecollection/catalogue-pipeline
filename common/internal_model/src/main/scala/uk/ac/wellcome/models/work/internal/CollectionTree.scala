package uk.ac.wellcome.models.work.internal

import uk.ac.wellcome.models.work.internal.result._

import cats.data.NonEmptyList
import cats.implicits._

/**
  * A CollectionTree contains a hierarchical archive or collection of works.
  * Each work in the hierarchy contains a forward slash separated path
  * representing its position.
  */
case class CollectionTree(
  path: CollectionPath,
  work: IdentifiedWork,
  children: List[CollectionTree] = Nil
) {

  def size: Int =
    children.map(_.size).sum + 1

  def pathList: List[String] =
    path.path :: children.flatMap(_.pathList)

  def isRoot: Boolean =
    path.depth == 1
}

object CollectionTree {

  /**
    * Create a CollectionTree from a list of works. This errors given the following
    * situations:
    *
    *   * If the works do not form a fully connected tree
    *   * The given list contains works with duplicate paths.
    *   * There are works which do not contain a Collection
    *   * The list is empty
    */
  def apply(works: List[IdentifiedWork]): Result[CollectionTree] =
    works
      .map { work =>
        work.data.collectionPath match {
          case Some(path) => Right(path -> work)
          case None =>
            Left(
              new Exception(
                s"Work ${work.canonicalId} is not part of a collection"))
        }
      }
      .toResult
      .flatMap {
        case head :: tail => Right(apply(NonEmptyList(head, tail), depth = 1))
        case Nil          => Left(new Exception("Cannot create empty tree"))
      }
      .flatMap { tree =>
        checkTreeErrors(tree, works).map(Left(_)).getOrElse(Right(tree))
      }

  private def apply(workMapping: NonEmptyList[(CollectionPath, IdentifiedWork)],
                    depth: Int): CollectionTree =
    workMapping.sortBy { case (path, _) => path.tokens.length } match {
      case NonEmptyList((path, work), tail) =>
        val childDepth = depth + 1
        val (children, descendents) = tail.span(_._1.depth == childDepth)
        CollectionTree(
          path = path,
          work = work,
          children = children.map {
            case (childPath, childWork) =>
              val childDescendents = descendents.filter {
                case (path, _) => path.isDescendent(childPath)
              }
              apply(
                NonEmptyList(childPath -> childWork, childDescendents),
                childDepth)
          }
        )
    }

  private def checkTreeErrors(tree: CollectionTree,
                              works: List[IdentifiedWork]) = {
    val pathList = tree.pathList
    if (pathList.length < works.length) {
      val unconnected =
        works.map(_.data.collectionPath.get.path).filterNot(pathList.toSet)
      Some(new Exception(
        s"Not all works in collection are connected to root '${tree.path.path}': ${unconnected
          .mkString(",")}"))
    } else if (pathList.length > pathList.toSet.size) {
      val duplicates = pathList.diff(pathList.distinct).distinct
      Some(
        new Exception(
          s"Tree contains duplicate paths: ${duplicates.mkString(",")}"))
    } else
      None
  }
}
