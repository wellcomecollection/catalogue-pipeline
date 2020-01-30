package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.searches.DateHistogramInterval
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.requests.searches.aggs.{
  DateHistogramAggregation,
  TermsAggregation,
  TopHitsAggregation
}
import com.sksamuel.elastic4s.requests.searches.queries.{
  BoolQuery,
  Query,
  RangeQuery
}
import com.sksamuel.elastic4s.requests.searches.sort.{FieldSort, SortOrder}
import com.sksamuel.elastic4s.{ElasticDate, Index}
import uk.ac.wellcome.display.models.{
  AggregationRequest,
  ProductionDateSortRequest,
  SortingOrder
}
import uk.ac.wellcome.platform.api.models._

case class ElasticSearchRequestBuilder(
  index: Index,
  sortDefinitions: List[FieldSort],
  queryOptions: ElasticsearchQueryOptions) {

  lazy val request: SearchRequest = search(index)
    .aggs { filteredAggregationBuilder.filteredAggregations }
    .query { filteredQuery }
    .postFilter { postFilterQuery }
    .sortBy { sort ++ sortDefinitions }
    .limit { queryOptions.limit }
    .from { queryOptions.from }

  private lazy val filteredAggregationBuilder =
    new FiltersAndAggregationsBuilder(
      queryOptions.aggregations,
      queryOptions.filters,
      toAggregation,
      buildWorkFilterQuery
    )

  private def toAggregation(aggReq: AggregationRequest) = aggReq match {
    case AggregationRequest.WorkType =>
      TermsAggregation("workType")
        .size(100)
        .field("data.workType.id")
        .minDocCount(0)

    case AggregationRequest.ProductionDate =>
      DateHistogramAggregation("productionDates")
        .calendarInterval(DateHistogramInterval.Year)
        .field("data.production.dates.range.from")
        .minDocCount(1)

    // We don't split genres into concepts, as the data isn't great,
    // and for rendering isn't useful at the moment.
    case AggregationRequest.Genre =>
      TermsAggregation("genres")
        .size(20)
        .field("data.genres.concepts.label.keyword")
        .minDocCount(0)

    case AggregationRequest.Subject =>
      TermsAggregation("subjects")
        .size(20)
        .field("data.subjects.label.keyword")
        .minDocCount(0)

    case AggregationRequest.Language =>
      TermsAggregation("language")
        .size(200)
        .field("data.language.id")
        .minDocCount(0)
        .additionalField("data.language.label")

    case AggregationRequest.License =>
      TermsAggregation("license")
        .size(100)
        .field("data.items.locations.license.id")
        .minDocCount(0)
  }

  lazy val sort = queryOptions.sortBy
    .map {
      case ProductionDateSortRequest => "data.production.dates.range.from"
    }
    .map { FieldSort(_).order(sortOrder) }

  lazy val sortOrder = queryOptions.sortOrder match {
    case SortingOrder.Ascending  => SortOrder.ASC
    case SortingOrder.Descending => SortOrder.DESC
  }

  lazy val postFilterQuery: BoolQuery = boolQuery.filter {
    filteredAggregationBuilder.pairedFilters.map(buildWorkFilterQuery)
  }

  lazy val filteredQuery: BoolQuery = queryOptions.searchQuery
    .map { searchQuery =>
      ElasticsearchQueryBuilder(searchQuery).query
    }
    .getOrElse { boolQuery }
    .filter {
      (IdentifiedWorkFilter :: filteredAggregationBuilder.unpairedFilters)
        .map(buildWorkFilterQuery)
    }

  private def buildWorkFilterQuery(workFilter: WorkFilter): Query =
    workFilter match {
      case IdentifiedWorkFilter =>
        termQuery(field = "type", value = "IdentifiedWork")
      case ItemLocationTypeFilter(itemLocationTypeIds) =>
        termsQuery(
          field = "data.items.locations.locationType.id",
          values = itemLocationTypeIds)
      case WorkTypeFilter(workTypeIds) =>
        termsQuery(field = "data.workType.id", values = workTypeIds)
      case DateRangeFilter(fromDate, toDate) =>
        val (gte, lte) =
          (fromDate map ElasticDate.apply, toDate map ElasticDate.apply)
        RangeQuery("data.production.dates.range.from", lte = lte, gte = gte)
      case LanguageFilter(languageIds) =>
        termsQuery(field = "data.language.id", values = languageIds)
      case GenreFilter(genreQuery) =>
        simpleStringQuery(genreQuery)
          .field("data.genres.label")
          .defaultOperator("AND")
      case SubjectFilter(subjectQuery) =>
        simpleStringQuery(subjectQuery)
          .field("data.subjects.label")
          .defaultOperator("AND")
      case LicenseFilter(licenseIds) =>
        termsQuery(
          field = "data.items.locations.license.id",
          values = licenseIds)
    }

  implicit class EnhancedTermsAggregation(agg: TermsAggregation) {
    def additionalField(field: String): TermsAggregation =
      additionalFields(List(field))
    def additionalFields(fields: List[String]): TermsAggregation = {
      agg.subAggregations(
        TopHitsAggregation("sample_doc")
          .size(1)
          .fetchSource(fields.toArray ++ agg.field, Array())
      )
    }
  }
}
