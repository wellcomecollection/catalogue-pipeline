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
  ProductionDateSortRequest,
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
    case AggregationRequest.ProductionDate =>
      // We use `productionDates` here over `production.dates` to match the case classes, which we then serialise to
      // the JSON path later.
      DateHistogramAggregation("productionDates")
        .interval(DateHistogramInterval.Year)
        .field("production.dates.range.from")
        .minDocCount(1)

    // We don't split genres into concepts, as the data isn't great, and for rendering isn't useful at the moment.
    // But we've left it as a CompositeAggregation to scale when we need to.
    case AggregationRequest.Genre =>
      CompositeAggregation("genres").sources(
        List(
          TermsValueSource(
            "label",
            field = Some("genres.concepts.agent.label.raw"))
        )
      )

    case AggregationRequest.Subject =>
      CompositeAggregation("subjects").sources(
        List(
          TermsValueSource(
            "label",
            field = Some("subjects.agent.label.raw")
          )
        )
      )

    case AggregationRequest.Language =>
      CompositeAggregation("language").sources(
        List(
          TermsValueSource("id", field = Some("language.id"))
        )
      )
  }

  lazy val sort = queryOptions.sortBy
    .map {
      case ProductionDateSortRequest => "production.dates.range.from"
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
      case LanguageFilter(languageIds) =>
        termsQuery(field = "language.id", values = languageIds)
      case GenreFilter(genreQuery) =>
        matchQuery(field = "genres.label", value = genreQuery)
      case SubjectFilter(subjectQuery) =>
        matchQuery(field = "subjects.agent.label", value = subjectQuery)
    }
}
