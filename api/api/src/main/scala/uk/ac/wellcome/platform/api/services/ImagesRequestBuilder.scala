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
  ColorMustQuery,
  ImageFilter,
  ImageMustQuery,
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

  def request(searchOptions: SearchOptions, index: Index): SearchRequest =
    search(index)
      .query(
        searchOptions.searchQuery
          .map { q =>
            ImagesMultiMatcher(q.query)
          }
          .getOrElse(boolQuery)
          .must(
            buildImageMustQuery(searchOptions.safeMustQueries[ImageMustQuery])
          )
          .filter(
            buildImageFilterQuery(searchOptions.safeFilters[ImageFilter])
          )
      )
      .sortBy { sortBy(searchOptions) }
      .limit(searchOptions.pageSize)
      .from(PaginationQuery.safeGetFrom(searchOptions))

  def sortBy(searchOptions: SearchOptions): Seq[Sort] =
    if (searchOptions.searchQuery.isDefined || searchOptions.mustQueries.nonEmpty) {
      List(scoreSort(SortOrder.DESC), idSort)
    } else {
      List(idSort)
    }

  def buildImageFilterQuery(filters: Seq[ImageFilter]): Seq[Query] =
    filters.map {
      case LicenseFilter(licenseIds) =>
        termsQuery(field = "location.license.id", values = licenseIds)
    }

  def buildImageMustQuery(queries: List[ImageMustQuery]): Seq[Query] =
    queries.map {
      case ColorMustQuery(hexColors) =>
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
