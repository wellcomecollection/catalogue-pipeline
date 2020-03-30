package uk.ac.wellcome.models.work.internal

import uk.ac.wellcome.models.work.internal.result._

import cats.data.NonEmptyList
import cats.implicits._

/**
  * A CollectionPath represents the position of an individual work in a
  * collection hierarchy.
  */
case class CollectionPath(
  path: String,
  level: CollectionLevel,
  label: Option[String] = None,
) {

  lazy val tokens: List[String] =
    path.split("/").toList

  lazy val depth: Int =
    tokens.length

  def isDescendent(other: CollectionPath): Boolean =
    tokens.slice(0, other.depth) == other.tokens
}

sealed trait CollectionLevel

object CollectionLevel {
  object Collection extends CollectionLevel
  object Section extends CollectionLevel
  object Series extends CollectionLevel
  object Item extends CollectionLevel
}

/**
  * A Collection contains a hierarchical archive or collection of works. Each 
  * work in the hierarchy contains a forward slash separated path representing
  * its position. Note that this is built dynamically from the index by querying
  * the CollectionPath fields, rather than being stored directly on the model.
  */
case class Collection(
  path: CollectionPath,
  work: IdentifiedWork,
  children: List[Collection] = Nil
) {

  def size: Int =
    children.map(_.size).sum + 1

  def pathList: List[String] =
    path.path :: children.flatMap(_.pathList)

  def isRoot: Boolean =
    path.depth == 1
}

object Collection {

  /**
    * Create a Collection from a list of works. This errors given the following
    * situations:
    *
    *   * If the works do not form a fully connected tree
    *   * The given list contains works with duplicate paths.
    *   * There are works which do not contain a Collection
    *   * The list is empty
    */
  def apply(works: List[IdentifiedWork]): Result[Collection] =
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
                    depth: Int): Collection =
    workMapping.sortBy { case (path, _) => path.tokens.length } match {
      case NonEmptyList((path, work), tail) =>
        val childDepth = depth + 1
        val (children, descendents) = tail.span(_._1.depth == childDepth)
        Collection(
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

  private def checkTreeErrors(tree: Collection,
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
