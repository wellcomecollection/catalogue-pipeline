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
    extends ElasticsearchRequestBuilder[WorkFilter, WorkMustQuery] {

  import ElasticsearchRequestBuilder._

  val idSort: FieldSort = fieldSort("state.canonicalId").order(SortOrder.ASC)

  def request(searchOptions: SearchOptions[WorkFilter, WorkMustQuery],
              index: Index): SearchRequest = {
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
    implicit searchOptions: SearchOptions[WorkFilter, WorkMustQuery]) =
    new FiltersAndAggregationsBuilder(
      searchOptions.aggregations,
      searchOptions.filters,
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

    case AggregationRequest.Contributor =>
      TermsAggregation("contributors")
        .size(20)
        .field("state.derivedData.contributorAgents")
        .minDocCount(0)

    case AggregationRequest.Languages =>
      TermsAggregation("languages")
        .size(200)
        .field("data.languages.id")
        .minDocCount(0)

    case AggregationRequest.License =>
      TermsAggregation("license")
        .size(100)
        .field("data.items.locations.license.id")
        .minDocCount(0)

    case AggregationRequest.ItemLocationType =>
      TermsAggregation("locationType")
        .size(100)
        .field("data.items.locations.type")
        .minDocCount(0)
  }

  private def sortBy(
    implicit searchOptions: SearchOptions[WorkFilter, WorkMustQuery]) =
    if (searchOptions.searchQuery.isDefined || searchOptions.mustQueries.nonEmpty) {
      sort :+ scoreSort(SortOrder.DESC) :+ idSort
    } else {
      sort :+ idSort
    }

  private def sort(
    implicit searchOptions: SearchOptions[WorkFilter, WorkMustQuery]) =
    searchOptions.sortBy
      .map {
        case ProductionDateSortRequest => "data.production.dates.range.from"
      }
      .map { FieldSort(_).order(sortOrder) }

  private def sortOrder(
    implicit searchOptions: SearchOptions[WorkFilter, WorkMustQuery]) =
    searchOptions.sortOrder match {
      case SortingOrder.Ascending  => SortOrder.ASC
      case SortingOrder.Descending => SortOrder.DESC
    }

  private def postFilterQuery(
    implicit searchOptions: SearchOptions[WorkFilter, WorkMustQuery])
    : BoolQuery =
    boolQuery.filter {
      filteredAggregationBuilder.pairedFilters.map(buildWorkFilterQuery)
    }

  private def filteredQuery(
    implicit searchOptions: SearchOptions[WorkFilter, WorkMustQuery])
    : BoolQuery =
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
      case GenreFilter(genreQuery) =>
        searchQueryFilter("data.genres.label", genreQuery)
      case SubjectFilter(subjectQuery) =>
        searchQueryFilter("data.subjects.label", subjectQuery)
      case ContributorsFilter(contributorQueries) =>
        should(
          contributorQueries.map { query =>
            searchQueryFilter("data.contributors.agent.label", query)
          }
        )
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

  private def searchQueryFilter(field: String, query: String) =
    simpleStringQuery(query)
      .field(field)
      .defaultOperator("AND")
}
