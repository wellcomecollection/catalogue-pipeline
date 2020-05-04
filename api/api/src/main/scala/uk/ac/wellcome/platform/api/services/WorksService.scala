package uk.ac.wellcome.platform.api.services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import io.circe.Decoder
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.ElasticError
import com.sksamuel.elastic4s.requests.get.GetResponse
import com.sksamuel.elastic4s.requests.searches.{SearchHit, SearchResponse}
import uk.ac.wellcome.display.models._
import uk.ac.wellcome.models.work.internal.{IdentifiedBaseWork, IdentifiedWork}
import uk.ac.wellcome.platform.api.models._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.api.rest.{
  PaginatedSearchOptions,
  PaginationQuery
}

case class WorksSearchOptions(
  filters: List[DocumentFilter] = Nil,
  pageSize: Int = 10,
  pageNumber: Int = 1,
  aggregations: List[AggregationRequest] = Nil,
  sortBy: List[SortRequest] = Nil,
  sortOrder: SortingOrder = SortingOrder.Ascending,
  searchQuery: Option[SearchQuery] = None
) extends PaginatedSearchOptions

case class WorkQuery(query: String, queryType: SearchQueryType)

class WorksService(searchService: ElasticsearchService)(
  implicit ec: ExecutionContext) {

  def findWorkById(canonicalId: String)(
    index: Index): Future[Either[ElasticError, Option[IdentifiedBaseWork]]] =
    searchService
      .findResultById(canonicalId)(index)
      .map { result: Either[ElasticError, GetResponse] =>
        result.map { response: GetResponse =>
          if (response.exists)
            Some(jsonTo[IdentifiedBaseWork](response.sourceAsString))
          else None
        }
      }

  def listOrSearchWorks(index: Index, searchOptions: WorksSearchOptions)
    : Future[Either[ElasticError, ResultList[IdentifiedWork, Aggregations]]] =
    (searchOptions.searchQuery match {
      case Some(_) => searchService.queryResults
      case None    => searchService.listResults
    })(index, toElasticsearchQueryOptions(searchOptions))
      .map { _.map(createResultList) }

  private def toElasticsearchQueryOptions(
    worksSearchOptions: WorksSearchOptions): ElasticsearchQueryOptions =
    ElasticsearchQueryOptions(
      searchQuery = worksSearchOptions.searchQuery,
      filters = worksSearchOptions.filters,
      limit = worksSearchOptions.pageSize,
      aggregations = worksSearchOptions.aggregations,
      sortBy = worksSearchOptions.sortBy,
      sortOrder = worksSearchOptions.sortOrder,
      from = PaginationQuery.safeGetFrom(worksSearchOptions)
    )

  private def createResultList(searchResponse: SearchResponse)
    : ResultList[IdentifiedWork, Aggregations] = {
    ResultList(
      results = searchResponseToWorks(searchResponse),
      totalResults = searchResponse.totalHits.toInt,
      aggregations = searchResponseToAggregationResults(searchResponse)
    )
  }

  private def searchResponseToWorks(
    searchResponse: SearchResponse): List[IdentifiedWork] =
    searchResponse.hits.hits.map { h: SearchHit =>
      jsonTo[IdentifiedWork](h.sourceAsString)
    }.toList

  private def searchResponseToAggregationResults(
    searchResponse: SearchResponse): Option[Aggregations] = {
    Aggregations(searchResponse)
  }

  private def jsonTo[T <: IdentifiedBaseWork](document: String)(
    implicit decoder: Decoder[T]): T =
    fromJson[T](document) match {
      case Success(work) => work
      case Failure(e) =>
        throw new RuntimeException(
          s"Unable to parse JSON as Work ($e): $document"
        )
    }
}
