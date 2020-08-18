package uk.ac.wellcome.relation_embedder

import scala.concurrent.{ExecutionContext, Future}
import scala.annotation.tailrec
import scala.util.Try

import com.sksamuel.elastic4s.{ElasticClient, ElasticError, Index, Response}
import com.sksamuel.elastic4s.requests.searches.{
  MultiSearchRequest,
  MultiSearchResponse,
  SearchResponse
}
import com.sksamuel.elastic4s.requests.searches.SearchHit
import com.sksamuel.elastic4s.ElasticDsl._

import uk.ac.wellcome.models.work.internal._

import uk.ac.wellcome.models.work.internal.result._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.json.JsonUtil.fromJson

case class RelatedWork(id: String)

case class RelatedWorks(
  parts: List[RelatedWork],
  partOf: List[RelatedWork],
  precededBy: List[RelatedWork],
  succeededBy: List[RelatedWork],
)

trait RelatedWorksService {

  def apply(work: IdentifiedWork): Future[RelatedWorks]
}

class PathQueryRelatedWorksService(elasticClient: ElasticClient, index: Index)(
  implicit ec: ExecutionContext)
    extends RelatedWorksService {

  type PathToken = Either[Int, String]

  type TokenizedPath = List[PathToken]

  def apply(work: IdentifiedWork): Future[RelatedWorks] =
    work.data.collectionPath match {
      case None => Future.successful(RelatedWorks(Nil, Nil, Nil, Nil))
      case Some(CollectionPath(path, _, _)) =>
        executeMultiSearchRequest(
          RelatedWorkRequestBuilder(index, path).request
        ).flatMap { result =>
          val works = result.left
            .map(_.asException)
            .flatMap { searchResponses =>
              searchResponses.map { resp =>
                resp.hits.hits.toList.map(toWork).toResult
              }.toResult
            }
          val relatedWorks = works.flatMap {
            case List(children, siblings, ancestors) =>
              Right(toRelatedWorks(path, children, siblings, ancestors))
            case works =>
              Left(
                new Exception(
                  "Expected multisearch response containing 3 items")
              )
          }
          relatedWorks match {
            case Right(relatedWorks) => Future.successful(relatedWorks)
            case Left(err)           => Future.failed(err)
          }
        }
    }

  def executeMultiSearchRequest(request: MultiSearchRequest)
    : Future[Either[ElasticError, List[SearchResponse]]] =
    elasticClient
      .execute(request)
      .map(toEither)
      .map { response =>
        response.right.flatMap {
          case MultiSearchResponse(items) =>
            val results = items.map(_.response)
            val error = results.collectFirst { case Left(err) => err }
            error match {
              case Some(err) => Left(err)
              case None =>
                Right(
                  results.collect { case Right(resp) => resp }.toList
                )
            }
        }
      }

  def toEither[T](response: Response[T]): Either[ElasticError, T] =
    if (response.isError)
      Left(response.error)
    else
      Right(response.result)

  def toWork(hit: SearchHit): Result[IdentifiedWork] =
    fromJson[IdentifiedWork](hit.sourceAsString).toEither

  def toRelatedWorks(path: String,
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
    RelatedWorks(
      parts = children.sortBy(tokenizePath).toRelatedWorks,
      partOf = ancestors.sortBy(tokenizePath).toRelatedWorks,
      precededBy = precededBy.toRelatedWorks,
      succeededBy = succeededBy.toRelatedWorks
    )
  }

  def tokenizePath(path: String): TokenizedPath =
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

  implicit class WorkListOps(works: List[IdentifiedWork]) {

    def toRelatedWorks: List[RelatedWork] =
      works.map { work =>
        RelatedWork(work.sourceIdentifier.toString)
      }
  }
}
