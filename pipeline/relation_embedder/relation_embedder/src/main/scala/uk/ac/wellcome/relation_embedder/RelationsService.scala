package uk.ac.wellcome.relation_embedder

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.streams.ReactiveElastic._
import com.sksamuel.elastic4s.ElasticDsl._
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
  def getAffectedWorks(batch: Batch): Source[Work[Merged], NotUsed]

  /** For a given work return all works in the same archive.
    *
    * @param path The archive path
    * @return The works
    */
  def getCompleteTree(batch: Batch): Source[Work[Merged], NotUsed]
}

class PathQueryRelationsService(
  elasticClient: ElasticClient,
  index: Index,
  completeTreeScroll: Int = 1000,
  affectedWorksScroll: Int = 250)(implicit as: ActorSystem)
    extends RelationsService
    with Logging {

  val requestBuilder = RelationsRequestBuilder(index)

  def getAffectedWorks(batch: Batch): Source[Work[Merged], NotUsed] = {
    val request = requestBuilder.affectedWorks(batch, affectedWorksScroll)
    info(s"Querying affected works with ES request: ${elasticClient.show(request)}")
    Source
      .fromPublisher(elasticClient.publisher(request))
      .map(searchHit => searchHit.safeTo[Work[Merged]].get)
  }

  def getCompleteTree(batch: Batch): Source[Work[Merged], NotUsed] = {
    val request = requestBuilder.completeTree(batch, completeTreeScroll)
    info(s"Querying complete tree with ES request: ${elasticClient.show(request)}")
    Source
      .fromPublisher(elasticClient.publisher(request))
      .map(searchHit => searchHit.safeTo[Work.Visible[Merged]].get)
  }

}
