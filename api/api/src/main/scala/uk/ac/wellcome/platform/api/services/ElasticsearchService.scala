package uk.ac.wellcome.platform.api.services

import com.google.inject.{Inject, Singleton}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.get.GetResponse
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import com.sksamuel.elastic4s.{ElasticClient, ElasticError, Response}
import com.sksamuel.elastic4s.requests.searches.sort.{FieldSort, SortOrder}
import com.sksamuel.elastic4s.Index
import grizzled.slf4j.Logging
import uk.ac.wellcome.display.models.{
  AggregationRequest,
  SortRequest,
  SortingOrder
}
import uk.ac.wellcome.platform.api.models._

import scala.concurrent.{ExecutionContext, Future}

case class ElasticsearchQueryOptions(filters: List[WorkFilter],
                                     limit: Int,
                                     from: Int,
                                     aggregations: List[AggregationRequest],
                                     sortBy: List[SortRequest],
                                     sortOrder: SortingOrder)

@Singleton
class ElasticsearchService @Inject()(elasticClient: ElasticClient)(
  implicit ec: ExecutionContext
) extends Logging {

  def findResultById(canonicalId: String)(
    index: Index): Future[Either[ElasticError, GetResponse]] =
    elasticClient
      .execute {
        get(canonicalId).from(index.name)
      }
      .map { toEither }

  def listResults: (Index, ElasticsearchQueryOptions) => Future[
    Either[ElasticError, SearchResponse]] =
    executeSearch(
      maybeWorkQuery = None,
      sortDefinitions = List(fieldSort("canonicalId").order(SortOrder.ASC))
    )

  def queryResults(
    workQuery: WorkQuery): (Index, ElasticsearchQueryOptions) => Future[
    Either[ElasticError, SearchResponse]] =
    executeSearch(
      maybeWorkQuery = Some(workQuery),
      sortDefinitions = List(
        fieldSort("_score").order(SortOrder.DESC),
        fieldSort("canonicalId").order(SortOrder.ASC))
    )

  /** Given a set of query options, build a SearchDefinition for Elasticsearch
    * using the elastic4s query DSL, then execute the search.
    */
  private def executeSearch(
    maybeWorkQuery: Option[WorkQuery],
    sortDefinitions: List[FieldSort]
  )(index: Index, queryOptions: ElasticsearchQueryOptions)
    : Future[Either[ElasticError, SearchResponse]] = {

    val searchRequest = ElastsearchSearchRequestBuilder(
      index,
      maybeWorkQuery,
      sortDefinitions,
      queryOptions
    ).request

    debug(s"Sending ES request: ${searchRequest.show}")

    elasticClient
      .execute { searchRequest.trackTotalHits(true) }
      .map { toEither }
  }

  private def toEither[T](response: Response[T]): Either[ElasticError, T] =
    if (response.isError) {
      Left(response.error)
    } else {
      Right(response.result)
    }
}
