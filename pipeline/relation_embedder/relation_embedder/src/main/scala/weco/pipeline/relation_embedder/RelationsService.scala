package weco.pipeline.relation_embedder

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.{ElasticClient, Index}
import grizzled.slf4j.Logging
import weco.json.JsonUtil._
import weco.catalogue.internal_model.Implicits._
import com.sksamuel.elastic4s.pekko.streams._
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
  affectedWorksScroll: Int = 250
)(implicit as: ActorSystem)
    extends RelationsService
    with Logging {

  private val requestBuilder = RelationsRequestBuilder(index)

  def getAffectedWorks(batch: Batch): Source[Work[Merged], NotUsed] = {
    val request = requestBuilder.affectedWorks(batch, affectedWorksScroll)
    debug(
      s"Querying affected works with ES request: ${elasticClient.show(request)}"
    )
    val sourceSettings = SourceSettings(search = request, maxItems = Long.MaxValue, fetchThreshold = affectedWorksScroll, warm = true)
    Source.fromGraph(new ElasticSource(elasticClient, sourceSettings)(as.dispatcher)).map(searchHit => searchHit.safeTo[Work[Merged]].get)
  }

  def getRelationTree(batch: Batch): Source[RelationWork, NotUsed] = {
    val request = requestBuilder.completeTree(batch, completeTreeScroll)
    debug(
      s"Querying complete tree with ES request: ${elasticClient.show(request)}"
    )
    val sourceSettings = SourceSettings(search = request, maxItems = Long.MaxValue, fetchThreshold = affectedWorksScroll, warm = true)
    Source.fromGraph(new ElasticSource(elasticClient, sourceSettings)(as.dispatcher))
      .map(searchHit => searchHit.safeTo[RelationWork].get)
  }
}
