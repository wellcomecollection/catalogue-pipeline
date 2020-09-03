package uk.ac.wellcome.relation_embedder

import scala.concurrent.{ExecutionContext, Future}

import com.sksamuel.elastic4s.{ElasticClient, ElasticError, Index, Response}
import com.sksamuel.elastic4s.requests.searches.{
  MultiSearchRequest,
  MultiSearchResponse,
  SearchRequest,
  SearchResponse
}
import com.sksamuel.elastic4s.requests.searches.SearchHit
import com.sksamuel.elastic4s.ElasticDsl._
import io.circe.generic.semiauto.deriveDecoder

import uk.ac.wellcome.models.work.internal._

import uk.ac.wellcome.models.work.internal.result._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.json.JsonUtil.fromJson

trait RelatedWorksService {

  /** Given some work, return the IDs of all other works which need to be
    * denormalised. This should consist of the works siblings, its parent, and
    * all its descendents.
    *
    * @param work The work
    * @return The IDs of the other works to denormalise
    */
  def getOtherAffectedWorks(
    work: IdentifiedBaseWork): Future[List[SourceIdentifier]]

  /** For a given work return all its relations.
    *
    * @param work The work
    * @return The related works which are embedded into the given work
    */
  def getRelations(work: IdentifiedBaseWork): Future[RelatedWorks]
}

class PathQueryRelatedWorksService(elasticClient: ElasticClient, index: Index)(
  implicit ec: ExecutionContext)
    extends RelatedWorksService {

  def getOtherAffectedWorks(
    work: IdentifiedBaseWork): Future[List[SourceIdentifier]] =
    work match {
      case work: IdentifiedWork =>
        work.data.collectionPath match {
          case None => Future.successful(Nil)
          case Some(CollectionPath(path, _, _)) =>
            executeSearchRequest(
              RelatedWorkRequestBuilder(index, path).otherAffectedWorksRequest
            ).flatMap { result =>
              val works = result.left
                .map(_.asException)
                .flatMap { resp =>
                  resp.hits.hits.toList.map(toAffectedWork).toResult
                }
              works match {
                case Right(works) =>
                  Future.successful(works.map(_.sourceIdentifier))
                case Left(err) => Future.failed(err)
              }
            }
        }
      case _ => Future.successful(Nil)
    }

  def getRelations(work: IdentifiedBaseWork): Future[RelatedWorks] =
    work match {
      case work: IdentifiedWork =>
        work.data.collectionPath match {
          case None => Future.successful(RelatedWorks.nil)
          case Some(CollectionPath(path, _, _)) =>
            executeMultiSearchRequest(
              RelatedWorkRequestBuilder(index, path).relationsRequest
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
                  Right(RelatedWorks(path, children, siblings, ancestors))
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
      case _ => Future.successful(RelatedWorks.nil)
    }

  private def executeSearchRequest(
    request: SearchRequest): Future[Either[ElasticError, SearchResponse]] =
    elasticClient.execute(request).map(toEither)

  private def executeMultiSearchRequest(request: MultiSearchRequest)
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

  private def toEither[T](response: Response[T]): Either[ElasticError, T] =
    if (response.isError)
      Left(response.error)
    else
      Right(response.result)

  private def toWork(hit: SearchHit): Result[IdentifiedWork] =
    fromJson[IdentifiedWork](hit.sourceAsString).toEither

  case class AffectedWork(sourceIdentifier: SourceIdentifier)

  implicit val affectedWorkDecoder = deriveDecoder[AffectedWork]

  private def toAffectedWork(hit: SearchHit): Result[AffectedWork] =
    fromJson[AffectedWork](hit.sourceAsString).toEither
}
