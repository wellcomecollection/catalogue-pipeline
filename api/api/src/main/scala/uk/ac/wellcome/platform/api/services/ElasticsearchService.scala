package uk.ac.wellcome.platform.api.services

import scala.concurrent.{ExecutionContext, Future}

import co.elastic.apm.api.Transaction
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.get.GetResponse
import com.sksamuel.elastic4s.requests.searches.{
  MultiSearchRequest,
  MultiSearchResponse,
  SearchRequest,
  SearchResponse
}
import com.sksamuel.elastic4s.{ElasticClient, ElasticError, Index, Response}
import grizzled.slf4j.Logging

import uk.ac.wellcome.display.models._
import uk.ac.wellcome.platform.api.Tracing
import uk.ac.wellcome.platform.api.models._

case class ElasticsearchQueryOptions(filters: List[DocumentFilter],
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

  def executeGet(canonicalId: String)(
    index: Index): Future[Either[ElasticError, GetResponse]] =
    withActiveTrace(elasticClient.execute {
      get(canonicalId).from(index.name)
    }).map { toEither }

  /** Given a set of query options, build a SearchDefinition for Elasticsearch
    * using the elastic4s query DSL, then execute the search.
    */
  def executeSearch(
    queryOptions: ElasticsearchQueryOptions,
    requestBuilder: ElasticsearchRequestBuilder,
    index: Index): Future[Either[ElasticError, SearchResponse]] = {
    val searchRequest = requestBuilder
      .request(
        queryOptions,
        index,
        scored = queryOptions.searchQuery.isDefined
      )
      .trackTotalHits(true)
    Tracing.currentTransaction.addQueryOptionLabels(queryOptions)
    executeSearchRequest(searchRequest)
  }

  def executeSearchRequest(
    request: SearchRequest): Future[Either[ElasticError, SearchResponse]] =
    spanFuture(
      name = "ElasticSearch#executeQuery",
      spanType = "request",
      subType = "elastic",
      action = "query"
    ) {
      debug(s"Sending ES request: ${request.show}")
      val transaction = Tracing.currentTransaction
      withActiveTrace(elasticClient.execute(request))
        .map(toEither)
        .map { responseOrError =>
          responseOrError.map { res =>
            transaction.addLabel("elasticTook", res.took)
            res
          }
        }
    }

  def executeMultiSearchRequest(request: MultiSearchRequest)
    : Future[Either[ElasticError, List[SearchResponse]]] =
    spanFuture(
      name = "ElasticSearch#executeQuery",
      spanType = "request",
      subType = "elastic",
      action = "query"
    ) {
      debug(s"Sending ES multirequest: ${request.show}")
      val transaction = Tracing.currentTransaction
      withActiveTrace(elasticClient.execute(request))
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
                    results.collect {
                      case Right(resp) =>
                        transaction.addLabel("elasticTook", resp.took)
                        resp
                    }.toList
                  )
              }
          }
        }
    }

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
