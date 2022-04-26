package weco.pipeline.relation_embedder

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.streams.ReactiveElastic._
import com.sksamuel.elastic4s.{ElasticClient, Index}
import grizzled.slf4j.Logging
import weco.json.JsonUtil._
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.catalogue.internal_model.work.Work
import weco.pipeline.relation_embedder.models.{Batch, RelationWork}

trait RelationsService {
  def getRelationTree(batch: Batch): Source[RelationWork, NotUsed]

  def getAffectedWorks(batch: Batch): Source[Work[Merged], NotUsed]
}

class PathQueryRelationsService(
  elasticClient: ElasticClient,
  index: Index,
  completeTreeScroll: Int = 1000,
  affectedWorksScroll: Int = 250)(implicit as: ActorSystem)
    extends RelationsService
    with Logging {

  private val requestBuilder = RelationsRequestBuilder(index)

  def getAffectedWorks(batch: Batch): Source[Work[Merged], NotUsed] = {
    val request = requestBuilder.affectedWorks(batch, affectedWorksScroll)
    debug(
      s"Querying affected works with ES request: ${elasticClient.show(request)}")
    Source
      .fromPublisher(elasticClient.publisher(request))
      .map(searchHit => searchHit.safeTo[Work[Merged]].get)
  }

  /**
   * Given a root path, return an Akka Source that generates RelationWork objects for every Work within that path.
   * @param batch
   * @return
   */
  def getRelationTree(batch: Batch): Source[RelationWork, NotUsed] = {
    val request = requestBuilder.completeTree(batch, completeTreeScroll)
    debug(
      s"Querying complete tree with ES request: ${elasticClient.show(request)}")
    Source
      .fromPublisher(elasticClient.publisher(request))
      .map(searchHit => searchHit.safeTo[RelationWork].get)
  }
}
