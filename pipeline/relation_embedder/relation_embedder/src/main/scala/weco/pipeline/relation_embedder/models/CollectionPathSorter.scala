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
    path
      .split("/")
      .map(parsePathToken)
      .toList

  // Given a single part of a path, parse it into a PathToken.
  //
  // This means identifying the runs of numbers and letters within the path,
  //
  // e.g. parsePathToken("ab1cd") = List(Right(ab), Left(1), Right(cd)
  //
  private def parsePathToken(s: String): PathToken =
    """\d+|\D+""".r
      .findAllIn(s)
      .toList
      .map {
        part =>
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

  implicit val pathTokenPartOrdering: Ordering[PathTokenPart] =
    (x: PathTokenPart, y: PathTokenPart) =>
      (x, y) match {
        case (Left(numberX), Left(numberY)) => numberX.compareTo(numberY)
        case (Right(strX), Right(strY))     => strX.compareTo(strY)
        case (Left(_), _)                   => -1
        case _                              => 1
      }

  def shorterPathsWinOrdering[T](
    perItemOrdering: Ordering[T]
  ): Ordering[List[T]] =
    new Ordering[List[T]] {
      @tailrec
      override def compare(x: List[T], y: List[T]): Int =
        (x, y) match {
          case (Nil, Nil) => 0

          // Shorter lists sort higher, e.g. "A/B" sorts above "A/B/C".
          //
          // Also within a single path, shorter parts sort higher,
          // e.g. "1" sorts above "1a"
          case (Nil, _) => -1
          case (_, Nil) => 1

          // Otherwise compare the first part, then compare subsequent
          // parts if they're the same.
          case (xHead :: xTail, yHead :: yTail) =>
            if (xHead == yHead)
              compare(xTail, yTail)
            else
              perItemOrdering.compare(xHead, yHead)
        }
    }

  implicit val pathTokenOrdering: Ordering[PathToken] =
    shorterPathsWinOrdering(pathTokenPartOrdering)

  implicit val tokenizedPathOrdering: Ordering[TokenizedPath] =
    shorterPathsWinOrdering(pathTokenOrdering)
}
