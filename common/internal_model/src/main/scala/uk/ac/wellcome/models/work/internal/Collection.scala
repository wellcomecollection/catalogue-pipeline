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
  level: Option[CollectionLevel] = None,
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
  work: Option[IdentifiedWork],
  children: List[Collection] = Nil
)

object Collection {

  /**
    * Create a Collection from a list of works. This errors given the following
    * situations:
    *
    *   * If the works are not all connected to the same root
    *   * The given list contains works with duplicate paths.
    *   * There are works which do not contain a Collection
    *   * The list is empty
    */
  def apply(works: List[IdentifiedWork]): Result[Collection] =
    works
      .map { work =>
        work.data.collectionPath match {
          case Some(_) => Right(work)
          case None =>
            Left(
              new Exception(
                s"Work ${work.canonicalId} is not part of a collection"))
        }
      }
      .toResult
      .map(getWorkMapping(_))
      .flatMap { mapping =>
        checkErrors(works, mapping).map(Left(_)).getOrElse {
          mapping match {
            case head :: tail =>
              Right(apply(NonEmptyList(head, tail), depth = 1))
            case Nil => Left(new Exception("Cannot create empty tree"))
          }
        }
      }

  private def getWorkMapping(works: List[IdentifiedWork])
    : List[(CollectionPath, Option[IdentifiedWork])] = {
    val partialMapping = works
      .map(work => work.data.collectionPath.get.path -> work)
      .toMap
    partialMapping.keys
      .flatMap(path => path :: ancestorPaths(path))
      .toList
      .sorted
      .distinct
      .map { path =>
        val work = partialMapping.get(path)
        work
          .map(_.data.collectionPath.get)
          .getOrElse(CollectionPath(path)) -> work
      }
  }

  private def ancestorPaths(path: String): List[String] =
    path.split("/").toList match {
      case parentTokens :+ _ if parentTokens.nonEmpty =>
        val parent = parentTokens.mkString("/")
        parent :: ancestorPaths(parent)
      case _ => Nil
    }

  private def apply(
    workMapping: NonEmptyList[(CollectionPath, Option[IdentifiedWork])],
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

  private def checkErrors(
    works: List[IdentifiedWork],
    mapping: List[(CollectionPath, Option[IdentifiedWork])]) = {
    val rootPaths = mapping.map { case (path, _) => path.tokens.head }.distinct
    val workPaths = works.map(_.data.collectionPath.get.path)
    val duplicates = workPaths.diff(workPaths.distinct).distinct
    if (rootPaths.length > 1) {
      Some(
        new Exception(
          s"Multiple root paths not permitted: ${rootPaths.mkString(", ")}"))
    } else if (duplicates.nonEmpty) {
      Some(
        new Exception(
          s"Tree contains duplicate paths: ${duplicates.mkString(",")}"))
    } else {
      None
    }
  }
}
