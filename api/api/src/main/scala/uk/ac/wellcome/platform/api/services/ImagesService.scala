package uk.ac.wellcome.platform.api.services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import com.sksamuel.elastic4s.{ElasticError, Hit, Index}
import com.sksamuel.elastic4s.circe._
import io.circe.Decoder

import uk.ac.wellcome.display.models.SortingOrder
import uk.ac.wellcome.models.work.internal.AugmentedImage
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.platform.api.Tracing
import uk.ac.wellcome.platform.api.models._
import uk.ac.wellcome.platform.api.rest.{
  PaginatedSearchOptions,
  PaginationQuery
}

case class ImagesSearchOptions(
  searchQuery: Option[SearchQuery] = None,
  filters: List[ImageFilter] = Nil,
  pageSize: Int = 10,
  pageNumber: Int = 1
) extends PaginatedSearchOptions

class ImagesService(searchService: ElasticsearchService)(
  implicit ec: ExecutionContext)
    extends Tracing {

  private val nVisuallySimilarImages = 5

  def findImageById(id: String)(
    index: Index): Future[Either[ElasticError, Option[AugmentedImage]]] =
    searchService
      .executeGet(id)(index)
      .map {
        _.map { response =>
          if (response.exists)
            Some(deserialize[AugmentedImage](response))
          else
            None
        }
      }

  def listOrSearchImages(index: Index, searchOptions: ImagesSearchOptions)
    : Future[Either[ElasticError, ResultList[AugmentedImage, Unit]]] =
    searchService
      .executeSearch(
        queryOptions = toElasticsearchQueryOptions(searchOptions),
        requestBuilder = ImagesRequestBuilder,
        index = index
      )
      .map { _.map(createResultList) }

  def retrieveSimilarImages(index: Index,
                            image: AugmentedImage,
                            similarityMetric: SimilarityMetric =
                              SimilarityMetric.Blended)
    : Future[List[AugmentedImage]] =
    searchService
      .executeSearchRequest({
        val requestBuilder = similarityMetric match {
          case SimilarityMetric.Blended =>
            ImagesRequestBuilder.requestWithBlendedSimilarity
          case SimilarityMetric.Features =>
            ImagesRequestBuilder.requestWithSimilarFeatures
          case SimilarityMetric.Colors =>
            ImagesRequestBuilder.requestWithSimilarColors
        }
        requestBuilder(index, image.id.canonicalId, nVisuallySimilarImages)
      })
      .map { result =>
        result
          .map { response =>
            response.hits.hits
              .map(hit => deserialize[AugmentedImage](hit))
              .toList
          }
          .getOrElse(Nil)
      }

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
      results = searchResponse.hits.hits.map(deserialize[AugmentedImage]).toList,
      totalResults = searchResponse.totalHits.toInt,
      aggregations = None
    )

  private def deserialize[T](hit: Hit)(implicit decoder: Decoder[T]): T =
    hit.safeTo[T] match {
      case Success(work) => work
      case Failure(e) =>
        throw new RuntimeException(
          s"Unable to parse JSON($e): ${hit.sourceAsString}"
        )
    }
}
