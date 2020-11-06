package uk.ac.wellcome.relation_embedder

import scala.util.Try
import scala.annotation.tailrec

import uk.ac.wellcome.models.work.internal._
import WorkState.Merged

object CollectionPathSorter {

  def sortWorks(works: List[Work[Merged]]): List[Work[Merged]] =
    works.sortBy(tokenizePath)

  def sortPaths(paths: List[String]): List[String] =
    paths.sortBy(tokenizePath)

  type TokenizedPath = List[PathToken]
  type PathToken = List[PathTokenPart]
  type PathTokenPart = Either[Int, String]

  private def tokenizePath(path: String): TokenizedPath =
    path.split("/").toList.map { str =>
      """\d+|\D+""".r
        .findAllIn(str)
        .toList
        .map { token =>
          Try(token.toInt).map(Left(_)).getOrElse(Right(token))
        }
    }

  private def tokenizePath(work: Work[Merged]): Option[TokenizedPath] =
    work.data.collectionPath
      .map { collectionPath =>
        tokenizePath(collectionPath.path)
      }

  implicit val tokenizedPathOrdering: Ordering[TokenizedPath] =
    new Ordering[TokenizedPath] {
      @tailrec
      override def compare(a: TokenizedPath, b: TokenizedPath): Int =
        (a, b) match {
          case (Nil, Nil) => 0
          case (Nil, _)   => -1
          case (_, Nil)   => 1
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
