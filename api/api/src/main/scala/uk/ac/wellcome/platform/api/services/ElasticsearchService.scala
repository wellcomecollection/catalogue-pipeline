package uk.ac.wellcome.platform.api.services

import com.google.inject.{Inject, Singleton}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.get.GetResponse
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import com.sksamuel.elastic4s.{ElasticClient, ElasticError, Response}
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.requests.searches.aggs.{
  CompositeAggregation,
  TermsValueSource
}
import com.sksamuel.elastic4s.requests.searches.queries.Query
import com.sksamuel.elastic4s.requests.searches.sort.{FieldSort, SortOrder}
import com.sksamuel.elastic4s.Index
import grizzled.slf4j.Logging
import uk.ac.wellcome.display.models.{
  AggregationRequest,
  ProductionDateFromSortRequest,
  ProductionDateToSortRequest,
  SortRequest,
  WorkTypeAggregationRequest
}
import uk.ac.wellcome.platform.api.models._

import scala.concurrent.{ExecutionContext, Future}

case class ElasticsearchQueryOptions(filters: List[WorkFilter],
                                     limit: Int,
                                     from: Int,
                                     aggregations: List[AggregationRequest],
                                     sort: List[SortRequest])

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

    val searchRequest: SearchRequest =
      buildSearchRequest(index, maybeWorkQuery, sortDefinitions, queryOptions)

    debug(s"Sending ES request: ${searchRequest.show}")

    elasticClient
      .execute { searchRequest.trackTotalHits(true) }
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

    val aggregations = queryOptions.aggregations flatMap {
      case _: WorkTypeAggregationRequest =>
        Some(
          CompositeAggregation(
            "workType",
            sources = List(
              TermsValueSource(name = "label", field = Some("workType.label")),
              TermsValueSource(name = "id", field = Some("workType.id")),
              TermsValueSource(
                name = "type",
                field = Some("workType.ontologyType"))
            )
          ))
      case _ => None
    }

    val sort = queryOptions.sort flatMap {
      case _: ProductionDateFromSortRequest =>
        Some(FieldSort("production.dates.range.from"))
      case _: ProductionDateToSortRequest =>
        Some(FieldSort("production.dates.range.to"))
      case _ => None
    }

    val limit = if (aggregations.nonEmpty) 0 else queryOptions.limit

    search(index)
      .aggs(aggregations)
      .query(queryDefinition)
      .sortBy(sort ++ sortDefinitions)
      .limit(limit)
      .from(queryOptions.from)
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
      filters.map { _.query } :+ termQuery(
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
