package uk.ac.wellcome.platform.api.services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.annotation.tailrec

import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.requests.searches.SearchHit

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.work.internal.result._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.json.JsonUtil.fromJson
import uk.ac.wellcome.platform.api.Tracing

case class RelatedWorks(
  parts: List[IdentifiedWork],
  partOf: List[IdentifiedWork],
  preceededBy: List[IdentifiedWork],
  suceededBy: List[IdentifiedWork],
)

class RelatedWorkService(elasticsearchService: ElasticsearchService)(
  implicit ec: ExecutionContext)
    extends Tracing {

  type PathToken = Either[Int, String]

  type TokenizedPath = List[PathToken]

  def retrieveRelatedWorks(index: Index,
                           work: IdentifiedWork): Future[Result[RelatedWorks]] =
    work.data.collectionPath match {
      case None => Future.successful(Right(RelatedWorks(Nil, Nil, Nil, Nil)))
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
    fromJson[IdentifiedWork](hit.sourceAsString).toEither

  private def toRelatedWorks(path: String,
                             children: List[IdentifiedWork],
                             siblings: List[IdentifiedWork],
                             ancestors: List[IdentifiedWork]): RelatedWorks = {
    val mainPath = tokenizePath(path)
    val (preceededBy, suceededBy) = siblings.sortBy(tokenizePath).partition {
      work =>
        tokenizePath(work) match {
          case None => true
          case Some(workPath) =>
            tokenizedPathOrdering.compare(mainPath, workPath) >= 0
        }
    }
    RelatedWorks(
      parts = children.sortBy(tokenizePath),
      partOf = ancestors.sortBy(tokenizePath),
      preceededBy = preceededBy,
      suceededBy = suceededBy
    )
  }

  private def tokenizePath(path: String): TokenizedPath =
    path.split("/").toList.map { str =>
      Try(str.toInt).map(Left(_)).getOrElse(Right(str))
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
      override def compare(a: PathToken, b: PathToken): Int =
        (a, b) match {
          case (Left(a), Left(b))   => a.compareTo(b)
          case (Right(a), Right(b)) => a.compareTo(b)
          case (Left(_), _)         => -1
          case _                    => 1
        }
    }
}
