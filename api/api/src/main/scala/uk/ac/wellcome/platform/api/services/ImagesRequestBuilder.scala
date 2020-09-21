package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.requests.searches._
import com.sksamuel.elastic4s.requests.searches.queries.Query
import com.sksamuel.elastic4s.requests.searches.sort._
import uk.ac.wellcome.platform.api.elasticsearch.{
  ColorQuery,
  ImageSimilarity,
  ImagesMultiMatcher
}
import uk.ac.wellcome.platform.api.models.{
  ColorFilter,
  ImageFilter,
  LicenseFilter,
  QueryConfig
}

class ImagesRequestBuilder(queryConfig: QueryConfig)
    extends ElasticsearchRequestBuilder {

  val idSort: FieldSort = fieldSort("id.canonicalId").order(SortOrder.ASC)

  lazy val colorQuery = new ColorQuery(
    binSizes = queryConfig.paletteBinSizes
  )

  def request(queryOptions: ElasticsearchQueryOptions,
              index: Index,
              scored: Boolean): SearchRequest =
    search(index)
      .query(
        queryOptions.searchQuery
          .map { q =>
            ImagesMultiMatcher(q.query)
          }
          .getOrElse(boolQuery)
          .filter(queryOptions.filters.collect {
            case filter: ImageFilter => buildImageFilterQuery(filter)
          })
      )
      .sortBy {
        if (scored) {
          List(scoreSort(SortOrder.DESC), idSort)
        } else {
          List(idSort)
        }
      }
      .limit(queryOptions.limit)
      .from(queryOptions.from)

  def buildImageFilterQuery(imageFilter: ImageFilter): Query =
    imageFilter match {
      case LicenseFilter(licenseIds) =>
        termsQuery(field = "locationDeprecated.license.id", values = licenseIds)
      case ColorFilter(hexColors) =>
        colorQuery(field = "inferredData.palette", hexColors)
    }

  def requestWithBlendedSimilarity: (Index, String, Int) => SearchRequest =
    similarityRequest(ImageSimilarity.blended)

  def requestWithSimilarFeatures: (Index, String, Int) => SearchRequest =
    similarityRequest(ImageSimilarity.features)

  def requestWithSimilarColors: (Index, String, Int) => SearchRequest =
    similarityRequest(ImageSimilarity.color)

  private def similarityRequest(query: (String, Index) => Query)(
    index: Index,
    id: String,
    n: Int): SearchRequest =
    search(index)
      .query(query(id, index))
      .size(n)
}
