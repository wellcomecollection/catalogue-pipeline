package weco.pipeline.relation_embedder.models

import scala.annotation.tailrec
import scala.util.{Success, Try}

object CollectionPathSorter {
  def sortPaths(paths: Set[String]): List[String] =
    paths.toList.sortBy(tokenizePath)

  type TokenizedPath = List[PathToken]
  type PathToken = List[PathTokenPart]
  type PathTokenPart = Either[Int, String]

  private def tokenizePath(path: String): TokenizedPath =
    path.split("/")
      .map(parsePathToken)
      .toList

  // Given a single part of a path, parse it into a PathToken.
  //
  // This means identifying the runs of numbers and letters within the path,
  //
  // e.g. parsePathToken("ab1cd") = List(Right(ab), Left(1), Right(cd)
  //
  private def parsePathToken(s: String): PathToken =
    """\d+|\D+""".r.findAllIn(s).toList
      .map { part =>
        Try(part.toInt) match {
          case Success(number) => Left(number)
          case _               => Right(part)
        }
      }

  // Note: when calling compare(x, y), the result sign has the following meaning:
  //
  //    - negative if x < y
  //    - positive if x > y
  //    - zero otherwise (if x == y)
  //

  implicit val tokenizedPathOrdering: Ordering[TokenizedPath] =
    new Ordering[TokenizedPath] {
      @tailrec
      override def compare(x: TokenizedPath, y: TokenizedPath): Int =
        (x, y) match {
          case (Nil, Nil) => 0

          // Shorter paths sort higher, e.g. "A/B" sorts above "A/B/C".
          case (Nil, _) => -1
          case (_, Nil) => 1

          // Otherwise compare the first part, then compare subsequent
          // parts if they're the same.
          case (xHead :: xTail, yHead :: yTail) =>
            if (xHead == yHead)
              compare(xTail, yTail)
            else
              pathTokenOrdering.compare(xHead, yHead)
        }
    }

  implicit val pathTokenOrdering: Ordering[PathToken] =
    new Ordering[PathToken] {
      @tailrec
      override def compare(a: PathToken, b: PathToken): Int =
        (a, b) match {
          case (Nil, Nil) => 0
          case (Nil, _)   => 1
          case (_, Nil)   => -1
          case (aHead :: aTail, bHead :: bTail) =>
            val comparison = pathTokenPartOrdering.compare(aHead, bHead)
            if (comparison == 0)
              compare(aTail, bTail)
            else
              comparison
        }
    }

  implicit val pathTokenPartOrdering: Ordering[PathTokenPart] =
    new Ordering[PathTokenPart] {
      override def compare(a: PathTokenPart, b: PathTokenPart): Int =
        (a, b) match {
          case (Left(a), Left(b))   => a.compareTo(b)
          case (Right(a), Right(b)) => a.compareTo(b)
          case (Left(_), _)         => -1
          case _                    => 1
        }
    }
}
