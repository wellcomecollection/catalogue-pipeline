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

  /** For a given work return all works in the same archive.
    *
    * @param work The work
    * @return The works
    */
  def getAllWorksInArchive(work: Work[Merged]): Future[List[Work[Merged]]]
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

  def getAllWorksInArchive(work: Work[Merged]): Future[List[Work[Merged]]] =
    work match {
      case work: Work.Visible[Merged] => 
        work.data.collectionPath match {
          case None => Future.successful(Nil)
          case Some(CollectionPath(path, _, _)) =>
            executeSearchRequest(
              RelationsRequestBuilder(index, path).allRelationsRequest
            ).flatMap { case result =>
              val works = result.left
                .map(_.asException)
                .flatMap { resp =>
                  // Are you here because something is erroring when being decoded?
                  // Check that all the fields you need are in the lists in RelationsRequestBuilder!
                  resp.hits.hits.toList.map(toWork).toResult
                }
              works match {
                case Right(works) => Future.successful(works)
                case Left(err)    => Future.failed(err)
              }
            }
        }
      case _ => Future.successful(Nil)
    }

  private def executeSearchRequest(
    request: SearchRequest): Future[Either[ElasticError, SearchResponse]] =
    elasticClient.execute(request).map(toEither)

  private def toEither[T](response: Response[T]): Either[ElasticError, T] =
    if (response.isError)
      Left(response.error)
    else
      Right(response.result)

  private def toWork(hit: SearchHit): Result[Work.Visible[Merged]] =
    hit.safeTo[Work.Visible[Merged]].toEither
}
