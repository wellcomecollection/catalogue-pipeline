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
  QueryConfig,
  SearchOptions
}
import uk.ac.wellcome.platform.api.rest.PaginationQuery

class ImagesRequestBuilder(queryConfig: QueryConfig)
    extends ElasticsearchRequestBuilder {

  val idSort: FieldSort = fieldSort("id.canonicalId").order(SortOrder.ASC)

  lazy val colorQuery = new ColorQuery(
    binSizes = queryConfig.paletteBinSizes
  )

  def request(searchOptions: SearchOptions,
              index: Index,
              scored: Boolean): SearchRequest =
    search(index)
      .query(
        searchOptions.searchQuery
          .map { q =>
            ImagesMultiMatcher(q.query)
          }
          .getOrElse(boolQuery)
          .filter(
            buildImageFilterQuery(searchOptions.typedFilters[ImageFilter])
          )
      )
      .sortBy {
        if (scored) {
          List(scoreSort(SortOrder.DESC), idSort)
        } else {
          List(idSort)
        }
      }
      .limit(searchOptions.pageSize)
      .from(PaginationQuery.safeGetFrom(searchOptions))

  def buildImageFilterQuery(filters: Seq[ImageFilter]): Seq[Query] =
    filters.map {
      case LicenseFilter(licenseIds) =>
        termsQuery(field = "location.license.id", values = licenseIds)
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
