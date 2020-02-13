package uk.ac.wellcome.platform.api.services

import co.elastic.apm.api.Transaction
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
import uk.ac.wellcome.platform.api.Tracing
import uk.ac.wellcome.platform.api.models._

import scala.concurrent.{ExecutionContext, Future}

case class ElasticsearchQueryOptions(filters: List[WorkFilter],
                                     limit: Int,
                                     from: Int,
                                     aggregations: List[AggregationRequest],
                                     sortBy: List[SortRequest],
                                     sortOrder: SortingOrder,
                                     searchQuery: Option[SearchQuery])

class ElasticsearchService(elasticClient: ElasticClient)(
  implicit ec: ExecutionContext
) extends Logging
    with Tracing {

  def findResultById(canonicalId: String)(
    index: Index): Future[Either[ElasticError, GetResponse]] =
    withActiveTrace(elasticClient.execute {
      get(canonicalId).from(index.name)
    }).map { toEither }

  def listResults: (Index, ElasticsearchQueryOptions) => Future[
    Either[ElasticError, SearchResponse]] =
    executeSearch(
      sortDefinitions = List(fieldSort("canonicalId").order(SortOrder.ASC))
    )

  def queryResults: (Index, ElasticsearchQueryOptions) => Future[
    Either[ElasticError, SearchResponse]] =
    executeSearch(
      sortDefinitions = List(
        fieldSort("_score").order(SortOrder.DESC),
        fieldSort("canonicalId").order(SortOrder.ASC))
    )

  /** Given a set of query options, build a SearchDefinition for Elasticsearch
    * using the elastic4s query DSL, then execute the search.
    */
  private def executeSearch(
    sortDefinitions: List[FieldSort]
  )(index: Index, queryOptions: ElasticsearchQueryOptions)
    : Future[Either[ElasticError, SearchResponse]] =
    spanFuture(
      name = "ElasticSearch#executeSearch",
      spanType = "request",
      subType = "elastic",
      action = "query")({

      val searchRequest = ElasticsearchRequestBuilder(
        index,
        sortDefinitions,
        queryOptions
      ).request

      debug(s"Sending ES request: ${searchRequest.show}")
      val transaction = Tracing.currentTransaction
        .addQueryOptionLabels(queryOptions)

      withActiveTrace(
        elasticClient
          .execute { searchRequest.trackTotalHits(true) })
        .map { toEither }
        .map {
          _.map { res =>
            transaction.addLabel("elasticTook", res.took)
            res
          }
        }
    })

  private def toEither[T](response: Response[T]): Either[ElasticError, T] =
    if (response.isError) {
      Left(response.error)
    } else {
      Right(response.result)
    }

  implicit class EnhancedTransaction(transaction: Transaction) {
    def addQueryOptionLabels(
      queryOptions: ElasticsearchQueryOptions): Transaction = {
      transaction.addLabel("limit", queryOptions.limit)
      transaction.addLabel("from", queryOptions.from)
      transaction.addLabel("sortOrder", queryOptions.sortOrder.toString)
      transaction.addLabel(
        "sortBy",
        queryOptions.sortBy.map { _.toString }.mkString(","))
      transaction.addLabel(
        "filters",
        queryOptions.filters.map { _.toString }.mkString(","))
      transaction.addLabel(
        "aggregations",
        queryOptions.aggregations.map { _.toString }.mkString(","))
    }
  }
}
