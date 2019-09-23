package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.searches.DateHistogramInterval
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.requests.searches.aggs.{
  CompositeAggregation,
  DateHistogramAggregation,
  TermsValueSource,
}
import com.sksamuel.elastic4s.requests.searches.queries.{Query, RangeQuery}
import com.sksamuel.elastic4s.requests.searches.sort.{FieldSort, SortOrder}
import com.sksamuel.elastic4s.{ElasticDate, Index}
import uk.ac.wellcome.display.models.{
  AggregationRequest,
  ProductionDateFromSortRequest,
  ProductionDateToSortRequest,
  SortingOrder,
}

import uk.ac.wellcome.platform.api.models._

case class ElastsearchSearchRequestBuilder(
  index: Index,
  maybeWorkQuery: Option[WorkQuery],
  sortDefinitions: List[FieldSort],
  queryOptions: ElasticsearchQueryOptions) {

  lazy val request: SearchRequest = search(index)
    .aggs { aggregations }
    .query { filteredQuery }
    .sortBy { sort ++ sortDefinitions }
    .limit { if (aggregations.nonEmpty) 0 else queryOptions.limit }
    .from { queryOptions.from }

  lazy val aggregations = queryOptions.aggregations.map {
    case AggregationRequest.WorkType =>
      CompositeAggregation("workType").sources(
        List(
          TermsValueSource("label", field = Some("workType.label.raw")),
          TermsValueSource("id", field = Some("workType.id")),
          TermsValueSource("type", field = Some("workType.ontologyType"))
        )
      )
    case AggregationRequest.Date =>
      DateHistogramAggregation("date")
        .interval(DateHistogramInterval.Year)
        .field("production.dates.range.from")
        .minDocCount(1)
  }

  lazy val sort = queryOptions.sortBy
    .map {
      case ProductionDateFromSortRequest => "production.dates.range.from"
      case ProductionDateToSortRequest   => "production.dates.range.to"
    }
    .map { FieldSort(_).order(sortOrder) }

  lazy val sortOrder = queryOptions.sortOrder match {
    case SortingOrder.Ascending  => SortOrder.ASC
    case SortingOrder.Descending => SortOrder.DESC
  }

  lazy val filteredQuery = maybeWorkQuery
    .map { workQuery =>
      must(workQuery.query)
    }
    .getOrElse { boolQuery }
    .filter {
      (IdentifiedWorkFilter :: queryOptions.filters).map(buildWorkFilterQuery)
    }

  private def buildWorkFilterQuery(workFilter: WorkFilter): Query =
    workFilter match {
      case IdentifiedWorkFilter =>
        termQuery(field = "type", value = "IdentifiedWork")
      case ItemLocationTypeFilter(itemLocationTypeIds) =>
        termsQuery(
          field = "items.agent.locations.locationType.id",
          values = itemLocationTypeIds)
      case WorkTypeFilter(workTypeIds) =>
        termsQuery(field = "workType.id", values = workTypeIds)
      case DateRangeFilter(fromDate, toDate) =>
        val (gte, lte) =
          (fromDate map ElasticDate.apply, toDate map ElasticDate.apply)
        boolQuery should (
          RangeQuery("production.dates.range.from", lte = lte, gte = gte),
          RangeQuery("production.dates.range.to", lte = lte, gte = gte)
        )
    }
}
