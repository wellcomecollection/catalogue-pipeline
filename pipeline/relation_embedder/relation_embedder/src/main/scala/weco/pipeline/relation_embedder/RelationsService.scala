package weco.pipeline.relation_embedder

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.circe._
import com.sksamuel.elastic4s.{ElasticClient, HitReader, Index, RequestFailure, RequestSuccess}
import grizzled.slf4j.Logging
import weco.json.JsonUtil._
import weco.catalogue.internal_model.Implicits._
import com.sksamuel.elastic4s.requests.searches.{SearchHit, SearchRequest}
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.catalogue.internal_model.work.Work
import weco.pipeline.relation_embedder.lib.SearchIterator
import weco.pipeline.relation_embedder.models.{Batch, RelationWork}

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, DurationInt}

trait RelationsService {
  def getRelationTree(batch: Batch): Source[RelationWork, NotUsed]

  def getAffectedWorks(batch: Batch): Source[Work[Merged], NotUsed]
}

class PathQueryRelationsService(
  elasticClient: ElasticClient,
  index: Index,
  completeTreeScroll: Int = 1000,
  affectedWorksScroll: Int = 250
) extends RelationsService
    with Logging {

  private val requestBuilder = RelationsRequestBuilder(index)

  def getAffectedWorks(batch: Batch): Source[Work[Merged], NotUsed] = {
    val request = requestBuilder.affectedWorks(batch, affectedWorksScroll)
    debug(
      s"Querying affected works with ES request: ${elasticClient.show(request)}"
    )
    // Arbitrary timeout value, it has to exist for SearchIterator,
    // but it has not been derived either through experimentation or calculation,
    implicit val timeout: Duration = 5 minutes

    Source.fromIterator(
      () =>
        SearchIterator.iterate[Work[Merged]](
          elasticClient,
          request
        )
    )
  }

  def getRelationTree(batch: Batch): Source[RelationWork, NotUsed] = {
    val request = requestBuilder.completeTree(batch, completeTreeScroll)
    debug(
      s"Querying complete tree with ES request: ${elasticClient.show(request)}"
    )

    implicit val timeout: Duration = 5 minutes

    Source.fromIterator(
      () =>
        SearchIterator.iterate[RelationWork](
          elasticClient,
          request
        )
    )
  }
}
