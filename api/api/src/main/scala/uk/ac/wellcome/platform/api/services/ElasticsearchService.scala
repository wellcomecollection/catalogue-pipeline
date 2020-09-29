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
import com.sksamuel.elastic4s.{ElasticClient, ElasticError, Index}
import grizzled.slf4j.Logging
import uk.ac.wellcome.platform.api.Tracing
import uk.ac.wellcome.platform.api.models._

class ElasticsearchService(elasticClient: ElasticClient)(
  implicit ec: ExecutionContext
) extends Logging
    with Tracing {

  def executeGet(canonicalId: String)(
    index: Index): Future[Either[ElasticError, GetResponse]] =
    withActiveTrace(elasticClient.execute {
      get(index, canonicalId)
    }).map { _.toEither }

  /** Given a set of query options, build a SearchDefinition for Elasticsearch
    * using the elastic4s query DSL, then execute the search.
    */
  def executeSearch(
    searchOptions: SearchOptions,
    requestBuilder: ElasticsearchRequestBuilder,
    index: Index): Future[Either[ElasticError, SearchResponse]] = {
    val searchRequest = requestBuilder
      .request(searchOptions, index)
      .trackTotalHits(true)
    Tracing.currentTransaction.addQueryOptionLabels(searchOptions)
    executeSearchRequest(searchRequest)
  }

  def executeSearchRequest(
    request: SearchRequest): Future[Either[ElasticError, SearchResponse]] =
    spanFuture(
      name = "ElasticSearch#executeSearchRequest",
      spanType = "request",
      subType = "elastic",
      action = "query"
    ) {
      debug(s"Sending ES request: ${request.show}")
      val transaction = Tracing.currentTransaction
      withActiveTrace(elasticClient.execute(request))
        .map(_.toEither)
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
      name = "ElasticSearch#executeMultiSearchRequest",
      spanType = "request",
      subType = "elastic",
      action = "query"
    ) {
      debug(s"Sending ES multirequest: ${request.show}")
      val transaction = Tracing.currentTransaction
      withActiveTrace(elasticClient.execute(request))
        .map(_.toEither)
        .map { response =>
          response.right.flatMap {
            case MultiSearchResponse(items) =>
              val results = items.map(_.response)
              val error = results.collectFirst { case Left(err) => err }
              error match {
                case Some(err) => Left(err)
                case None =>
                  val responses = results.collect { case Right(resp) => resp }.toList
                  val took = responses.map(_.took).sum
                  transaction.addLabel("elasticTook", took)
                  Right(responses)
              }
          }
        }
    }

  implicit class EnhancedTransaction(transaction: Transaction) {
    def addQueryOptionLabels(searchOptions: SearchOptions): Transaction = {
      transaction.addLabel("pageSize", searchOptions.pageSize)
      transaction.addLabel("pageNumber", searchOptions.pageNumber)
      transaction.addLabel("sortOrder", searchOptions.sortOrder.toString)
      transaction.addLabel(
        "sortBy",
        searchOptions.sortBy.map { _.toString }.mkString(","))
      transaction.addLabel(
        "filters",
        searchOptions.filters.map { _.toString }.mkString(","))
      transaction.addLabel(
        "aggregations",
        searchOptions.aggregations.map { _.toString }.mkString(","))
    }
  }
}
