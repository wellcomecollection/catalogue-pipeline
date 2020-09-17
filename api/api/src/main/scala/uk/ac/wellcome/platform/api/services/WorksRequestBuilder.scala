package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.requests.searches._
import com.sksamuel.elastic4s.requests.searches.aggs._
import com.sksamuel.elastic4s.requests.searches.queries._
import com.sksamuel.elastic4s.requests.searches.sort._

import uk.ac.wellcome.display.models._
import uk.ac.wellcome.platform.api.models._

object WorksRequestBuilder extends ElasticsearchRequestBuilder {

  import ElasticsearchRequestBuilder._

  val idSort: FieldSort = fieldSort("state.canonicalId").order(SortOrder.ASC)

  def request(queryOptions: ElasticsearchQueryOptions,
              index: Index,
              scored: Boolean = false): SearchRequest = {
    implicit val q = queryOptions
    search(index)
      .aggs { filteredAggregationBuilder.filteredAggregations }
      .query { filteredQuery }
      .postFilter { postFilterQuery }
      .sortBy {
        if (scored) {
          sort :+ scoreSort(SortOrder.DESC) :+ idSort
        } else {
          sort :+ idSort
        }
      }
      .limit { queryOptions.limit }
      .from { queryOptions.from }
  }

  private def filteredAggregationBuilder(
    implicit queryOptions: ElasticsearchQueryOptions) =
    new FiltersAndAggregationsBuilder(
      queryOptions.aggregations,
      queryOptions.filters.collect { case filter: WorkFilter => filter },
      toAggregation,
      buildWorkFilterQuery
    )

  private def toAggregation(aggReq: AggregationRequest) = aggReq match {
    case AggregationRequest.Format =>
      TermsAggregation("format")
        .size(100)
        .field("data.format.id")
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

    // Because `Language`s are constructed using a top_hit, if the doc_count is
    // 0 then we cannot construct the `Language`. Therefore we have to have a
    // `min_doc_count` of 1 (the default) as we would if this were a composite
    // aggregation.
    case AggregationRequest.Language =>
      TermsAggregation("language")
        .size(200)
        .field("data.language.id")
        .minDocCount(1)
        .additionalField("data.language.label")

    case AggregationRequest.License =>
      TermsAggregation("license")
        .size(100)
        .field("data.items.locations.license.id")
        .minDocCount(0)
  }

  private def sort(implicit queryOptions: ElasticsearchQueryOptions) =
    queryOptions.sortBy
      .map {
        case ProductionDateSortRequest => "data.production.dates.range.from"
      }
      .map { FieldSort(_).order(sortOrder) }

  private def sortOrder(implicit queryOptions: ElasticsearchQueryOptions) =
    queryOptions.sortOrder match {
      case SortingOrder.Ascending  => SortOrder.ASC
      case SortingOrder.Descending => SortOrder.DESC
    }

  private def postFilterQuery(
    implicit queryOptions: ElasticsearchQueryOptions): BoolQuery =
    boolQuery.filter {
      filteredAggregationBuilder.pairedFilters.map(buildWorkFilterQuery)
    }

  private def filteredQuery(
    implicit queryOptions: ElasticsearchQueryOptions): BoolQuery =
    queryOptions.searchQuery
      .map {
        case SearchQuery(query, queryType) =>
          queryType.toEsQuery(query)
      }
      .getOrElse { boolQuery }
      .filter {
        (VisibleWorkFilter :: filteredAggregationBuilder.unpairedFilters)
          .map(buildWorkFilterQuery)
      }

  private def buildWorkFilterQuery(workFilter: WorkFilter): Query =
    workFilter match {
      case VisibleWorkFilter =>
        termQuery(field = "type", value = "Standard")
      case ItemLocationTypeFilter(itemLocationTypeIds) =>
        termsQuery(
          field = "data.items.locations.locationType.id",
          values = itemLocationTypeIds)
      case FormatFilter(formatIds) =>
        termsQuery(field = "data.format.id", values = formatIds)
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
      case IdentifiersFilter(identifiers) =>
        should(
          termsQuery(
            field = "state.sourceIdentifier.value",
            values = identifiers),
          termsQuery(
            field = "data.otherIdentifiers.value",
            values = identifiers)
        )
      case AccessStatusFilter(includes, excludes) =>
        includesExcludesQuery(
          field = "data.items.locations.accessConditions.status.type",
          includes = includes.map(_.name),
          excludes = excludes.map(_.name),
        )
      case CollectionPathFilter(path) =>
        termQuery(field = "data.collectionPath.path", value = path)
      case CollectionDepthFilter(depth) =>
        termQuery(field = "data.collectionPath.depth", value = depth)

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
