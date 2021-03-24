package uk.ac.wellcome.platform.api.services

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import com.sksamuel.elastic4s.{ElasticError, Hit, Index}
import com.sksamuel.elastic4s.circe._
import io.circe.Decoder
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.platform.api.Tracing
import uk.ac.wellcome.platform.api.models._
import weco.catalogue.internal_model.identifiers.CanonicalId
import weco.catalogue.internal_model.image.{Image, ImageState}

class ImagesService(searchService: ElasticsearchService,
                    queryConfig: QueryConfig)(implicit ec: ExecutionContext)
    extends Tracing {

  private val nVisuallySimilarImages = 5
  private val imagesRequestBuilder = new ImagesRequestBuilder(queryConfig)

  def findImageById(id: CanonicalId)(index: Index)
    : Future[Either[ElasticError, Option[Image[ImageState.Indexed]]]] =
    searchService
      .executeGet(id)(index)
      .map {
        _.map { response =>
          if (response.exists)
            Some(deserialize[Image[ImageState.Indexed]](response))
          else
            None
        }
      }

  def listOrSearchImages(index: Index, searchOptions: ImageSearchOptions)
    : Future[Either[ElasticError,
                    ResultList[Image[ImageState.Indexed], ImageAggregations]]] =
    searchService
      .executeSearch(
        searchOptions = searchOptions,
        requestBuilder = imagesRequestBuilder,
        index = index
      )
      .map { _.map(createResultList) }

  def retrieveSimilarImages(index: Index,
                            image: Image[ImageState.Indexed],
                            similarityMetric: SimilarityMetric =
                              SimilarityMetric.Blended)
    : Future[List[Image[ImageState.Indexed]]] =
    searchService
      .executeSearchRequest({
        val requestBuilder = similarityMetric match {
          case SimilarityMetric.Blended =>
            imagesRequestBuilder.requestWithBlendedSimilarity
          case SimilarityMetric.Features =>
            imagesRequestBuilder.requestWithSimilarFeatures
          case SimilarityMetric.Colors =>
            imagesRequestBuilder.requestWithSimilarColors
        }
        requestBuilder(index, image.id, nVisuallySimilarImages)
      })
      .map { result =>
        result
          .map { response =>
            response.hits.hits
              .map(hit => deserialize[Image[ImageState.Indexed]](hit))
              .toList
          }
          .getOrElse(Nil)
      }

  def createResultList(searchResponse: SearchResponse)
    : ResultList[Image[ImageState.Indexed], ImageAggregations] =
    ResultList(
      results = searchResponse.hits.hits
        .map(deserialize[Image[ImageState.Indexed]])
        .toList,
      totalResults = searchResponse.totalHits.toInt,
      aggregations = ImageAggregations(searchResponse)
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
