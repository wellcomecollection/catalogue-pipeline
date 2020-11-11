package uk.ac.wellcome.relation_embedder

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.streams.ReactiveElastic._
import com.sksamuel.elastic4s.{ElasticClient, Index}
import grizzled.slf4j.Logging

import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.internal.WorkState.Merged
import uk.ac.wellcome.models.work.internal._

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
  def getAllWorksInArchive(work: Work[Merged]): Source[Work[Merged], NotUsed]
}

class PathQueryRelationsService(
  elasticClient: ElasticClient,
  index: Index,
  allArchiveWorksScroll: Int = 1000,
  affectedWorksScroll: Int = 250)(implicit as: ActorSystem)
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
                  RelationsRequestBuilder(index, path)
                    .otherAffectedWorksRequest(affectedWorksScroll)
                )
              )
              .map(searchHit => searchHit.safeTo[Work[Merged]].get)
        }
      case _ => Source.empty[Work[Merged]]
    }

  def getAllWorksInArchive(work: Work[Merged]): Source[Work[Merged], NotUsed] =
    work match {
      case work: Work.Visible[Merged] =>
        work.data.collectionPath match {
          case None => Source.empty[Work[Merged]]
          case Some(CollectionPath(path, _, _)) =>
            Source
              .fromPublisher(
                elasticClient.publisher(
                  RelationsRequestBuilder(index, path)
                    .allRelationsRequest(allArchiveWorksScroll)
                )
              )
              .map(searchHit => searchHit.safeTo[Work.Visible[Merged]].get)
        }
      case _ => Source.empty[Work[Merged]]
    }
}
