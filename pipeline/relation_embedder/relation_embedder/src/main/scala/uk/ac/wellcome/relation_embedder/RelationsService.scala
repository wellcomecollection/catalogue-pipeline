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
    * @param path The archive path
    * @return The IDs of the other works to denormalise
    */
  def getAffectedWorks(path: String): Source[Work[Merged], NotUsed]

  /** For a given work return all works in the same archive.
    *
    * @param path The archive path
    * @return The works
    */
  def getAllWorksInArchive(path: String): Source[Work[Merged], NotUsed]
}

class PathQueryRelationsService(
  elasticClient: ElasticClient,
  index: Index,
  allArchiveWorksScroll: Int = 1000,
  affectedWorksScroll: Int = 250)(implicit as: ActorSystem)
    extends RelationsService
    with Logging {

  def getAffectedWorks(path: String): Source[Work[Merged], NotUsed] =
    Source
      .fromPublisher(
        elasticClient.publisher(
          RelationsRequestBuilder(index, path)
            .otherAffectedWorksRequest(affectedWorksScroll)
        )
      )
      .map(searchHit => searchHit.safeTo[Work[Merged]].get)

  def getAllWorksInArchive(path: String): Source[Work[Merged], NotUsed] =
    Source
      .fromPublisher(
        elasticClient.publisher(
          RelationsRequestBuilder(index, path)
            .allRelationsRequest(allArchiveWorksScroll)
        )
      )
      .map(searchHit => searchHit.safeTo[Work.Visible[Merged]].get)

}
