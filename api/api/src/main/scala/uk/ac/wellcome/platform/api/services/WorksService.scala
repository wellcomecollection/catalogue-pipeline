package uk.ac.wellcome.platform.api.services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import io.circe.Decoder
import com.sksamuel.elastic4s.{Hit, Index}
import com.sksamuel.elastic4s.ElasticError
import com.sksamuel.elastic4s.requests.get.GetResponse
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import com.sksamuel.elastic4s.circe._

import uk.ac.wellcome.display.models._
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.api.models._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.platform.api.rest.{
  PaginatedSearchOptions,
  PaginationQuery
}
import WorkState.Identified

case class WorksSearchOptions(
  filters: List[WorkFilter] = Nil,
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
    index: Index): Future[Either[ElasticError, Option[Work[Identified]]]] =
    searchService
      .executeGet(canonicalId)(index)
      .map { result: Either[ElasticError, GetResponse] =>
        result.map { response: GetResponse =>
          if (response.exists)
            Some(deserialize[Work[Identified]](response))
          else None
        }
      }

  def listOrSearchWorks(index: Index, searchOptions: WorksSearchOptions)
    : Future[Either[ElasticError, ResultList[Work.Standard[Identified], Aggregations]]] =
    searchService
      .executeSearch(
        queryOptions = toElasticsearchQueryOptions(searchOptions),
        requestBuilder = WorksRequestBuilder,
        index = index
      )
      .map { _.map(createResultList) }

  private def toElasticsearchQueryOptions(
    worksSearchOptions: WorksSearchOptions) =
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
    : ResultList[Work.Standard[Identified], Aggregations] = {
    ResultList(
      results = searchResponseToWorks(searchResponse),
      totalResults = searchResponse.totalHits.toInt,
      aggregations = searchResponseToAggregationResults(searchResponse)
    )
  }

  private def searchResponseToWorks(
    searchResponse: SearchResponse): List[Work.Standard[Identified]] =
    searchResponse.hits.hits.map { hit =>
      deserialize[Work.Standard[Identified]](hit)
    }.toList

  private def searchResponseToAggregationResults(
    searchResponse: SearchResponse): Option[Aggregations] = {
    Aggregations(searchResponse)
  }

  private def deserialize[T](hit: Hit)(implicit decoder: Decoder[T]): T =
    hit.safeTo[T] match {
      case Success(work) => work
      case Failure(e) =>
        throw new RuntimeException(
          s"Unable to parse JSON($e): ${hit.sourceAsString}"
        )
    }
}
