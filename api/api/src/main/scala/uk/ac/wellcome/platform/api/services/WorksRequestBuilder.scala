package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.requests.searches._
import com.sksamuel.elastic4s.requests.searches.aggs._
import com.sksamuel.elastic4s.requests.searches.queries._
import com.sksamuel.elastic4s.requests.searches.sort._
import uk.ac.wellcome.display.models._
import uk.ac.wellcome.platform.api.models._
import uk.ac.wellcome.models.work.internal.WorkType
import uk.ac.wellcome.platform.api.rest.PaginationQuery

object WorksRequestBuilder
    extends ElasticsearchRequestBuilder[WorkSearchOptions] {

  import ElasticsearchRequestBuilder._

  val idSort: FieldSort = fieldSort("state.canonicalId").order(SortOrder.ASC)

  def request(searchOptions: WorkSearchOptions, index: Index): SearchRequest = {
    implicit val s = searchOptions
    search(index)
      .aggs { filteredAggregationBuilder.filteredAggregations }
      .query { filteredQuery }
      .postFilter { postFilterQuery }
      .sortBy { sortBy }
      .limit { searchOptions.pageSize }
      .from { PaginationQuery.safeGetFrom(searchOptions) }
  }

  private def filteredAggregationBuilder(
    implicit searchOptions: WorkSearchOptions) =
    new WorkFiltersAndAggregationsBuilder(
      aggregationRequests = searchOptions.aggregations,
      filters = searchOptions.filters,
      requestToAggregation = toAggregation,
      filterToQuery = buildWorkFilterQuery
    )

  private def toAggregation(aggReq: WorkAggregationRequest) = aggReq match {
    case WorkAggregationRequest.Format =>
      TermsAggregation("format")
        .size(100)
        .field("data.format.id")
        .minDocCount(0)

    case WorkAggregationRequest.ProductionDate =>
      DateHistogramAggregation("productionDates")
        .calendarInterval(DateHistogramInterval.Year)
        .field("data.production.dates.range.from")
        .minDocCount(1)

    // We don't split genres into concepts, as the data isn't great,
    // and for rendering isn't useful at the moment.
    case WorkAggregationRequest.Genre =>
      TermsAggregation("genres")
        .size(20)
        .field("data.genres.concepts.label.keyword")
        .minDocCount(0)

    case WorkAggregationRequest.Subject =>
      TermsAggregation("subjects")
        .size(20)
        .field("data.subjects.label.keyword")
        .minDocCount(0)

    case WorkAggregationRequest.Contributor =>
      TermsAggregation("contributors")
        .size(20)
        .field("state.derivedData.contributorAgents")
        .minDocCount(0)

    case WorkAggregationRequest.Languages =>
      TermsAggregation("languages")
        .size(200)
        .field("data.languages.id")
        .minDocCount(0)

    case WorkAggregationRequest.License =>
      TermsAggregation("license")
        .size(100)
        .field("data.items.locations.license.id")
        .minDocCount(0)

    case WorkAggregationRequest.ItemLocationType =>
      TermsAggregation("locationType")
        .size(100)
        .field("data.items.locations.type")
        .minDocCount(0)
  }

  private def sortBy(implicit searchOptions: WorkSearchOptions) =
    if (searchOptions.searchQuery.isDefined || searchOptions.mustQueries.nonEmpty) {
      sort :+ scoreSort(SortOrder.DESC) :+ idSort
    } else {
      sort :+ idSort
    }

  private def sort(implicit searchOptions: WorkSearchOptions) =
    searchOptions.sortBy
      .map {
        case ProductionDateSortRequest => "data.production.dates.range.from"
      }
      .map { FieldSort(_).order(sortOrder) }

  private def sortOrder(implicit searchOptions: WorkSearchOptions) =
    searchOptions.sortOrder match {
      case SortingOrder.Ascending  => SortOrder.ASC
      case SortingOrder.Descending => SortOrder.DESC
    }

  private def postFilterQuery(
    implicit searchOptions: WorkSearchOptions): BoolQuery =
    boolQuery.filter {
      filteredAggregationBuilder.pairedFilters.map(buildWorkFilterQuery)
    }

  private def filteredQuery(
    implicit searchOptions: WorkSearchOptions): BoolQuery =
    searchOptions.searchQuery
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
        termQuery(field = "type", value = "Visible")
      case FormatFilter(formatIds) =>
        termsQuery(field = "data.format.id", values = formatIds)
      case WorkTypeFilter(types) =>
        termsQuery(
          field = "data.workType",
          values = types.map(WorkType.getName))
      case DateRangeFilter(fromDate, toDate) =>
        val (gte, lte) =
          (fromDate map ElasticDate.apply, toDate map ElasticDate.apply)
        RangeQuery("data.production.dates.range.from", lte = lte, gte = gte)
      case LanguagesFilter(languageIds) =>
        termsQuery(field = "data.languages.id", values = languageIds)
      case GenreFilter(genreQueries) =>
        termsQuery("data.genres.label.keyword", genreQueries)
      case SubjectFilter(subjectQueries) =>
        termsQuery("data.subjects.label.keyword", subjectQueries)
      case ContributorsFilter(contributorQueries) =>
        termsQuery("data.contributors.agent.label.keyword", contributorQueries)
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
      case ItemLocationTypeFilter(locationTypes) =>
        termsQuery(
          field = "data.items.locations.type",
          values = locationTypes.map(_.name))
      case ItemLocationTypeIdFilter(itemLocationTypeIds) =>
        termsQuery(
          field = "data.items.locations.locationType.id",
          values = itemLocationTypeIds)
      case PartOfFilter(id) =>
        termQuery(field = "state.relations.ancestors.id", value = id)
    }
}
