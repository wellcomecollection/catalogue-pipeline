package uk.ac.wellcome.platform.api.services

import com.google.inject.{Inject, Singleton}
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.http.get.GetResponse
import com.sksamuel.elastic4s.http.search.SearchResponse
import com.sksamuel.elastic4s.http.{ElasticClient, ElasticError, Response}
import com.sksamuel.elastic4s.searches.SearchRequest
import com.sksamuel.elastic4s.searches.queries.Query
import com.sksamuel.elastic4s.searches.queries.term.TermsQuery
import com.sksamuel.elastic4s.searches.sort.{FieldSort, SortOrder}
import grizzled.slf4j.Logging
import uk.ac.wellcome.platform.api.models._

import scala.concurrent.{ExecutionContext, Future}

case class ElasticsearchQueryOptions(filters: List[WorkFilter],
                                     limit: Int,
                                     from: Int)

@Singleton
class ElasticsearchService @Inject()(elasticClient: ElasticClient)(
  implicit ec: ExecutionContext
) extends Logging {

  def findResultById(canonicalId: String)(
    index: Index): Future[Either[ElasticError, GetResponse]] =
    elasticClient
      .execute {
        get(canonicalId).from(index.name, index.name)
      }
      .map { toEither }

  def listResults: (Index, ElasticsearchQueryOptions) => Future[
    Either[ElasticError, SearchResponse]] =
    executeSearch(
      maybeWorkQuery = None,
      sortDefinitions = List(fieldSort("canonicalId").order(SortOrder.ASC))
    )

  def simpleStringQueryResults(
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

    val searchRequest: SearchRequest =
      buildSearchRequest(index, maybeWorkQuery, sortDefinitions, queryOptions)

    debug(s"Sending ES request: $searchRequest")

    elasticClient
      .execute { searchRequest }
      .map { toEither }
  }

  private def buildSearchRequest(index: Index,
                                 maybeWorkQuery: Option[WorkQuery],
                                 sortDefinitions: List[FieldSort],
                                 queryOptions: ElasticsearchQueryOptions) = {
    val queryDefinition: Query = buildFilteredQuery(
      maybeWorkQuery = maybeWorkQuery,
      filters = queryOptions.filters
    )

    search(index)
      .query(queryDefinition)
      .sortBy(sortDefinitions)
      .limit(queryOptions.limit)
      .from(queryOptions.from)
  }

  private def toTermQuery(workFilter: WorkFilter): TermsQuery[String] =
    workFilter match {
      case ItemLocationTypeFilter(itemLocationTypeIds) =>
        termsQuery(
          field = "items.agent.locations.locationType.id",
          values = itemLocationTypeIds)
      case WorkTypeFilter(workTypeIds) =>
        termsQuery(field = "workType.id", values = workTypeIds)
    }

  private def buildFilteredQuery(maybeWorkQuery: Option[WorkQuery],
                                 filters: List[WorkFilter]): Query = {
    val query = maybeWorkQuery match {
      case Some(workQuery) =>
        must(workQuery.query())
      case None =>
        boolQuery()
    }

    val filterDefinitions: List[Query] =
      filters.map { toTermQuery } :+ termQuery(
        field = "type",
        value = "IdentifiedWork")

    query.filter(filterDefinitions)
  }

  private def toEither[T](response: Response[T]): Either[ElasticError, T] =
    if (response.isError) {
      Left(response.error)
    } else {
      Right(response.result)
    }
}
