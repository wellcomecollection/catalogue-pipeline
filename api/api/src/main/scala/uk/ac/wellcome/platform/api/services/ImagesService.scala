package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.requests.searches.SearchResponse
import com.sksamuel.elastic4s.{ElasticError, Index}
import uk.ac.wellcome.display.models.SortingOrder
import uk.ac.wellcome.json.JsonUtil.fromJson
import uk.ac.wellcome.models.work.internal.AugmentedImage
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.platform.api.models.{ResultList, SearchQuery, WorkFilter}
import uk.ac.wellcome.platform.api.rest.{
  PaginatedSearchOptions,
  PaginationQuery
}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class ImagesSearchOptions(
  searchQuery: Option[SearchQuery] = None,
  filters: List[WorkFilter] = Nil,
  pageSize: Int = 10,
  pageNumber: Int = 1
) extends PaginatedSearchOptions

class ImagesService(searchService: ElasticsearchService)(
  implicit ec: ExecutionContext) {

  def findImageById(id: String)(
    index: Index): Future[Either[ElasticError, Option[AugmentedImage]]] =
    searchService
      .findResultById(id)(index)
      .map {
        _.map { response =>
          if (response.exists) {
            Some(fromJson[AugmentedImage](response.sourceAsString) match {
              case Success(image) => image
              case Failure(e) =>
                throw new RuntimeException(
                  s"Unable to parse JSON as Image ($e): ${response.sourceAsString}"
                )
            })
          } else {
            None
          }
        }
      }

  def listOrSearchImages(index: Index, searchOptions: ImagesSearchOptions)
    : Future[Either[ElasticError, ResultList[AugmentedImage, Unit]]] =
    searchService
      .executeSearch(
        queryOptions = toElasticsearchQueryOptions(searchOptions),
        index = index,
        scored = searchOptions.searchQuery.isDefined
      )
      .map { _.map(createResultList) }

  def toElasticsearchQueryOptions(
    options: ImagesSearchOptions): ElasticsearchQueryOptions =
    ElasticsearchQueryOptions(
      searchQuery = options.searchQuery,
      filters = options.filters,
      limit = options.pageSize,
      aggregations = Nil,
      from = PaginationQuery.safeGetFrom(options),
      sortBy = Nil,
      sortOrder = SortingOrder.Ascending
    )

  def createResultList(
    searchResponse: SearchResponse): ResultList[AugmentedImage, Unit] =
    ResultList(
      results =
        searchResponse.hits.hits.map(hit => jsonTo(hit.sourceAsString)).toList,
      totalResults = searchResponse.totalHits.toInt,
      aggregations = None
    )

  private def jsonTo(doc: String) = fromJson[AugmentedImage](doc) match {
    case Success(image) => image
    case Failure(e) =>
      throw new RuntimeException(
        s"Unable to parse JSON as Image ($e): $doc"
      )
  }
}
