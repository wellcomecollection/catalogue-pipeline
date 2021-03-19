package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.requests.searches._
import com.sksamuel.elastic4s.requests.searches.aggs.TermsAggregation
import com.sksamuel.elastic4s.requests.searches.queries.Query
import com.sksamuel.elastic4s.requests.searches.sort._
import uk.ac.wellcome.display.models.ImageAggregationRequest
import uk.ac.wellcome.platform.api.elasticsearch.{
  ColorQuery,
  ImageSimilarity,
  ImagesMultiMatcher
}
import uk.ac.wellcome.platform.api.models._
import uk.ac.wellcome.platform.api.rest.PaginationQuery
import weco.catalogue.internal_model.locations.License

class ImagesRequestBuilder(queryConfig: QueryConfig)
    extends ElasticsearchRequestBuilder[ImageSearchOptions] {

  val idSort: FieldSort = fieldSort("state.canonicalId").order(SortOrder.ASC)

  lazy val colorQuery = new ColorQuery(
    binSizes = queryConfig.paletteBinSizes,
    binMinima = queryConfig.paletteBinMinima
  )

  def request(searchOptions: ImageSearchOptions, index: Index): SearchRequest =
    search(index)
      .aggs { filteredAggregationBuilder(searchOptions).filteredAggregations }
      .query(
        searchOptions.searchQuery
          .map { q =>
            ImagesMultiMatcher(q.query)
          }
          .getOrElse(boolQuery)
          .must(
            buildImageMustQuery(searchOptions.mustQueries)
          )
          .filter(
            buildImageFilterQuery(searchOptions.filters)
          )
      )
      .sortBy { sortBy(searchOptions) }
      .limit(searchOptions.pageSize)
      .from(PaginationQuery.safeGetFrom(searchOptions))

  private def filteredAggregationBuilder(searchOptions: ImageSearchOptions) =
    new ImageFiltersAndAggregationsBuilder(
      aggregationRequests = searchOptions.aggregations,
      filters = searchOptions.filters,
      requestToAggregation = toAggregation,
      filterToQuery = buildImageFilterQuery
    )

  private def toAggregation(aggReq: ImageAggregationRequest) = aggReq match {
    case ImageAggregationRequest.License =>
      TermsAggregation("license")
        .size(License.values.size)
        .field("locations.license.id")
        .minDocCount(0)
    case ImageAggregationRequest.SourceContributorAgents =>
      TermsAggregation("sourceContributorAgents")
        .size(20)
        .field("state.derivedData.sourceContributorAgents")
        .minDocCount(0)
    case ImageAggregationRequest.SourceGenres =>
      TermsAggregation("sourceGenres")
        .size(20)
        .field("source.canonicalWork.data.genres.label.keyword")
        .minDocCount(0)
  }

  def sortBy(searchOptions: ImageSearchOptions): Seq[Sort] =
    if (searchOptions.searchQuery.isDefined || searchOptions.mustQueries.nonEmpty) {
      List(scoreSort(SortOrder.DESC), idSort)
    } else {
      List(idSort)
    }

  def buildImageFilterQuery(filter: ImageFilter): Query =
    filter match {
      case LicenseFilter(licenseIds) =>
        termsQuery(field = "locations.license.id", values = licenseIds)
      case ContributorsFilter(contributorQueries) =>
        termsQuery(
          "source.canonicalWork.data.contributors.agent.label.keyword",
          contributorQueries
        )
      case GenreFilter(genreQueries) =>
        termsQuery(
          "source.canonicalWork.data.genres.label.keyword",
          genreQueries)
    }

  def buildImageFilterQuery(filters: Seq[ImageFilter]): Seq[Query] =
    filters.map { buildImageFilterQuery }

  def buildImageMustQuery(queries: List[ImageMustQuery]): Seq[Query] =
    queries.map {
      case ColorMustQuery(hexColors) =>
        colorQuery(field = "state.inferredData.palette", hexColors)
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
