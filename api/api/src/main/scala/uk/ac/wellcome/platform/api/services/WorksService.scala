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

case class WorksSearchOptions(
  filters: List[WorkFilter] = Nil,
  pageSize: Int = 10,
  pageNumber: Int = 1,
  aggregations: List[AggregationRequest] = Nil,
  sortBy: List[SortRequest] = Nil,
  sortOrder: SortingOrder = SortingOrder.Ascending,
  searchQuery: Option[SearchQuery] = None
)

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
    : Future[Either[ElasticError, ResultList]] =
    searchOptions.searchQuery match {
      case Some(_) =>
        searchWorks(index, searchOptions)
      case None =>
        listWorks(index, searchOptions)
    }

  def listWorks(index: Index, worksSearchOptions: WorksSearchOptions)
    : Future[Either[ElasticError, ResultList]] =
    searchService
      .listResults(index, toElasticsearchQueryOptions(worksSearchOptions))
      .map { result: Either[ElasticError, SearchResponse] =>
        result.map { createResultList }
      }

  def searchWorks(index: Index, worksSearchOptions: WorksSearchOptions)
    : Future[Either[ElasticError, ResultList]] =
    searchService
      .queryResults(index, toElasticsearchQueryOptions(worksSearchOptions))
      .map { result: Either[ElasticError, SearchResponse] =>
        result.map { createResultList }
      }

  private def toElasticsearchQueryOptions(
    worksSearchOptions: WorksSearchOptions): ElasticsearchQueryOptions = {

    // Because we use Int for the pageSize and pageNumber, computing
    //
    //     from = (pageNumber - 1) * pageSize
    //
    // can potentially overflow and be negative or even wrap around.
    // For example, pageNumber=2018634700 and pageSize=100 would return
    // results if you don't handle this!
    //
    // If we are about to overflow, we pass the max possible int
    // into the Elasticsearch query and let it bubble up from there.
    // We could skip the query and throw here, because the user is almost
    // certainly doing something wrong, but that would mean simulating an
    // ES error or modifying our exception handling, and that seems more
    // disruptive than just clamping the overflow.
    //
    // Note: the checks on "pageSize" mean we know it must be
    // at most 100.

    // How this check works:
    //
    //    1.  If pageNumber > MaxValue, then (pageNumber - 1) * pageSize is
    //        probably bigger, as pageSize >= 1.
    //
    //    2.  Alternatively, we care about whether
    //
    //            pageSize * pageNumber > MaxValue
    //
    //        Since pageNumber is known positive, this is equivalent to
    //
    //            pageSize > MaxValue / pageNumber
    //
    //        And we know the division won't overflow because we have
    //        pageValue < MaxValue by the first check.
    //
    val willOverflow =
      (worksSearchOptions.pageNumber > Int.MaxValue) ||
        (worksSearchOptions.pageSize > Int.MaxValue / worksSearchOptions.pageNumber)

    val from = if (willOverflow) {
      Int.MaxValue
    } else {
      (worksSearchOptions.pageNumber - 1) * worksSearchOptions.pageSize
    }

    assert(
      from >= 0,
      message = s"from = $from < 0, which is daft.  Has something overflowed?"
    )

    ElasticsearchQueryOptions(
      searchQuery = worksSearchOptions.searchQuery,
      filters = worksSearchOptions.filters,
      limit = worksSearchOptions.pageSize,
      aggregations = worksSearchOptions.aggregations,
      sortBy = worksSearchOptions.sortBy,
      sortOrder = worksSearchOptions.sortOrder,
      from = from
    )
  }

  private def createResultList(searchResponse: SearchResponse): ResultList = {
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
