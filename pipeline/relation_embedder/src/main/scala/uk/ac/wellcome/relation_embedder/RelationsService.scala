package uk.ac.wellcome.relation_embedder

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.requests.searches._
import com.sksamuel.elastic4s.streams.ReactiveElastic._
import com.sksamuel.elastic4s.{ElasticClient, ElasticError, Index, Response}
import grizzled.slf4j.Logging
import uk.ac.wellcome.json.JsonUtil.fromJson
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.WorkState.Merged
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.work.internal.result._

import scala.concurrent.{ExecutionContext, Future}

trait RelationsService {

  /** Given some work, return the IDs of all other works which need to be
    * denormalised. This should consist of the works siblings, its parent, and
    * all its descendents.
    *
    * @param work The work
    * @return The IDs of the other works to denormalise
    */
  def getOtherAffectedWorks(work: Work[Merged]): Source[Work[Merged], NotUsed]

  /** For a given work return all its relations.
    *
    * @param work The work
    * @return The related works which are embedded into the given work
    */
  def getRelations(
    work: Work[Merged]): Future[Relations[DataState.Unidentified]]
}

class PathQueryRelationsService(
  elasticClient: ElasticClient,
  index: Index,
  scrollSize: Int)(implicit ec: ExecutionContext, as: ActorSystem)
    extends RelationsService
    with Logging {

  def getOtherAffectedWorks(work: Work[Merged]): Source[Work[Merged], NotUsed] =
    work match {
      case work: Work.Visible[Merged] =>
        work.data.collectionPath match {
          case None =>
            info(
              s"work ${work.id} does not belong to an archive, skipping getOtherAffectedWorks.")
            Source.empty[Work[Merged]]
          case Some(CollectionPath(path, _, _)) =>
            Source
              .fromPublisher(
                elasticClient.publisher(
                  RelationsRequestBuilder(index, path).otherAffectedWorksRequest
                    .scroll(keepAlive = "5m")
                    .size(scrollSize)))
              .map { searchHit: SearchHit =>
                fromJson[Work[Merged]](searchHit.sourceAsString).get
              }
        }
      case _ => Source.empty[Work[Merged]]
    }

  def getRelations(
    work: Work[Merged]): Future[Relations[DataState.Unidentified]] =
    work match {
      case work: Work.Visible[Merged] =>
        work.data.collectionPath match {
          case None =>
            Future.successful(Relations.none)
          case Some(CollectionPath(path, _, _)) =>
            executeMultiSearchRequest(
              RelationsRequestBuilder(index, path).relationsRequest
            ).flatMap { result =>
              val works = result.left
                .map(_.asException)
                .flatMap { searchResponses =>
                  searchResponses.map { resp =>
                    resp.hits.hits.toList.map(toWork).toResult
                  }.toResult
                }
              val relations = works.flatMap {
                case List(children, siblings, ancestors) =>
                  Right(
                    ArchiveRelationsBuilder(path, children, siblings, ancestors)
                  )
                case _ =>
                  Left(
                    new Exception(
                      "Expected multisearch response containing 3 items")
                  )
              }
              relations match {
                case Right(relations) => Future.successful(relations)
                case Left(err)        => Future.failed(err)
              }
            }
        }
      case _ => Future.successful(Relations.none)
    }

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

  private def toWork(hit: SearchHit): Result[Work.Visible[Merged]] =
    hit.safeTo[Work.Visible[Merged]].toEither
}
