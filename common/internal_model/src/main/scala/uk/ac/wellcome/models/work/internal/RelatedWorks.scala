package uk.ac.wellcome.models.work.internal

import scala.util.Try
import scala.annotation.tailrec

/** Holds relations for a particular work. This is a recursive data structure,
  * so related works can in turn hold their relations. An Option[List[_]] is
  * used to represent cases both where the work has no relations and where the
  * relations are not currently known.
  *
  * @param parts Children of the work
  * @param partOf Parents of the work
  * @param precededBy Siblings preceding the work
  * @param succeededBy Siblings following the work
  */
case class RelatedWorks(
  parts: Option[List[RelatedWork]] = None,
  partOf: Option[List[RelatedWork]] = None,
  precededBy: Option[List[RelatedWork]] = None,
  succeededBy: Option[List[RelatedWork]] = None,
)

case class RelatedWork(
  work: Work[WorkState.Identified, Id.Identified],
  relatedWorks: RelatedWorks
)

object RelatedWorks {

  type IdentifiedWork = Work[WorkState.Identified, Id.Identified]

  def unknown: RelatedWorks =
    RelatedWorks(
      parts = None,
      partOf = None,
      precededBy = None,
      succeededBy = None
    )

  def nil: RelatedWorks =
    RelatedWorks(
      parts = Some(Nil),
      partOf = Some(Nil),
      precededBy = Some(Nil),
      succeededBy = Some(Nil)
    )

  def partOf(relatedWork: RelatedWork,
             default: Option[List[RelatedWork]] = None): RelatedWorks =
    RelatedWorks(
      parts = default,
      partOf = Some(List(relatedWork)),
      precededBy = default,
      succeededBy = default
    )

  type TokenizedPath = List[PathToken]
  type PathToken = List[PathTokenPart]
  type PathTokenPart = Either[Int, String]

  /** Creates RelatedWorks from unnested and unsorted data.
    *
    * @param path Children of the work
    * @param children Direct descendents of the work
    * @param siblings Siblings of the work
    * @param ancestors Ancestors of the work
    */
  def apply(path: String,
            children: List[IdentifiedWork],
            siblings: List[IdentifiedWork],
            ancestors: List[IdentifiedWork]): RelatedWorks = {
    val mainPath = tokenizePath(path)
    val (precededBy, succeededBy) = siblings.sortBy(tokenizePath).partition {
      work =>
        tokenizePath(work) match {
          case None => true
          case Some(workPath) =>
            tokenizedPathOrdering.compare(mainPath, workPath) >= 0
        }
    }
    val parent = ancestors.sortBy(tokenizePath) match {
      case head :: tail =>
        Some(
          tail.foldLeft(RelatedWork(head, RelatedWorks(partOf = Some(Nil)))) {
            case (relatedWork, work) =>
              RelatedWork(work, RelatedWorks.partOf(relatedWork))
          }
        )
      case Nil => None
    }

    RelatedWorks(
      parts = Some(children.sortBy(tokenizePath).map(RelatedWork(_))),
      partOf = Some(parent.toList),
      precededBy = Some(precededBy.map(RelatedWork(_))),
      succeededBy = Some(succeededBy.map(RelatedWork(_)))
    )
  }

  private def tokenizePath(path: String): TokenizedPath =
    path.split("/").toList.map { str =>
      """\d+|\D+""".r
        .findAllIn(str)
        .toList
        .map { token =>
          Try(token.toInt).map(Left(_)).getOrElse(Right(token))
        }
    }

  private def tokenizePath(work: IdentifiedWork): Option[TokenizedPath] =
    work
      .maybeData
      .flatMap { data => 
        data.collectionPath
        .map { collectionPath =>
          tokenizePath(collectionPath.path)
        }
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

object RelatedWork {

  type IdentifiedWork = Work[WorkState.Identified, Id.Identified]

  def apply(work: IdentifiedWork): RelatedWork =
    RelatedWork(work, RelatedWorks.unknown)
}
