package uk.ac.wellcome.platform.api.services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.annotation.tailrec

import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.requests.searches.SearchHit
import com.sksamuel.elastic4s.circe._

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.work.internal.result._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.platform.api.Tracing

class RelatedWorkService(elasticsearchService: ElasticsearchService)(
  implicit ec: ExecutionContext)
    extends Tracing {

  type TokenizedPath = List[PathToken]
  type PathToken = List[PathTokenPart]
  type PathTokenPart = Either[Int, String]

  def retrieveRelatedWorks(index: Index,
                           work: IdentifiedWork): Future[Result[RelatedWorks]] =
    work.data.collectionPath match {
      case None =>
        Future.successful(Right(RelatedWorks.nil))
      case Some(CollectionPath(path, _, _)) =>
        elasticsearchService
          .executeMultiSearchRequest(
            RelatedWorkRequestBuilder(index, path).request
          )
          .map { result =>
            val works = result.left
              .map(_.asException)
              .flatMap { searchResponses =>
                searchResponses.map { resp =>
                  resp.hits.hits.toList.map(toWork).toResult
                }.toResult
              }
            works.flatMap {
              case List(children, siblings, ancestors) =>
                Right(toRelatedWorks(path, children, siblings, ancestors))
              case works =>
                Left(
                  new Exception(
                    "Expected multisearch response containing 3 items")
                )
            }
          }
    }

  private def toWork(hit: SearchHit): Result[IdentifiedWork] =
    hit.safeTo[IdentifiedWork].toEither

  private def toRelatedWorks(path: String,
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
