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
  path: String,
  work: IdentifiedWork,
  children: List[CollectionTree] = Nil
) {

  def size: Int =
    children.map(_.size).sum + 1

  def pathList: List[String] =
    path :: children.flatMap(_.pathList)

  def isRoot: Boolean =
    !path.contains("/")
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
        work.data.collection match {
          case Some(Collection(_, path)) => Right((tokenize(path), work))
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

  private type Path = List[String]

  private def apply(workMapping: NonEmptyList[(Path, IdentifiedWork)],
                    depth: Int): CollectionTree =
    workMapping.sortBy { case (path, _) => path.length } match {
      case NonEmptyList((path, work), tail) =>
        val childDepth = depth + 1
        val (children, descendents) = tail.span(_._1.length == childDepth)
        CollectionTree(
          path = join(path),
          work = work,
          children = children.map {
            case (childPath, childWork) =>
              val childDescendents = descendents.filter {
                case (path, _) => isDescendentPath(path, childPath)
              }
              apply(
                NonEmptyList((childPath, childWork), childDescendents),
                childDepth)
          }
        )
    }

  private def checkTreeErrors(tree: CollectionTree,
                              works: List[IdentifiedWork]) = {
    val pathList = tree.pathList
    if (pathList.length < works.length) {
      val unconnected =
        works.map(_.data.collection.get.path).filterNot(pathList.toSet)
      Some(
        new Exception(
          s"Not all works in collection are connected to root '${tree.path}': ${unconnected
            .mkString(",")}"))
    } else if (pathList.length > pathList.toSet.size) {
      val duplicates = pathList.diff(pathList.distinct).distinct
      Some(
        new Exception(
          s"Tree contains duplicate paths: ${duplicates.mkString(",")}"))
    } else
      None
  }

  private def tokenize(path: String): Path =
    path.split("/").toList

  private def join(tokens: List[String]): String =
    tokens.mkString("/")

  private def isDescendentPath(path: Path, other: Path): Boolean =
    path.slice(0, other.length) == other
}
