package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.searches.aggs.{
  AbstractAggregation,
  Aggregation,
  FilterAggregation
}
import com.sksamuel.elastic4s.requests.searches.queries.Query
import uk.ac.wellcome.display.models.AggregationRequest
import uk.ac.wellcome.platform.api.models._

import scala.collection.immutable._

/** This class governs the way in which we wish to combine the filters and aggregations
  * that are specified for a search. We have a concept of "pairing" a filter and an aggregation:
  * for example, an aggregation on workType is paired with a filter of a specific workType.
  * If a search includes an aggregation and its paired filter, the filter is *not* applied to that
  * aggregation, but is still applied to results and to all other aggregations.
  *
  * Given a list of aggregations requests and filters, as well as functions to convert these to
  * constituents of the ES query, this class exposes:
  *
  * - `aggregations`: a list of all the ES query aggregations, where those that need to be filtered
  *   now have a sub-aggregation of the filter aggregation type, named "filtered".
  * - `independentFilters`: a list of all of the given filters which are not paired to any of
  *   the given aggregations. These can be used as the ES query filters.
  * - `aggregationDependentFilters`: a list of all of the given filters which are paired to one
  *   of the given aggregations. These can be used as the ES post-query filters.
  */
class FilteredAggregationBuilder(
  aggregationRequests: List[AggregationRequest],
  filters: List[WorkFilter],
  requestToAggregation: AggregationRequest => Aggregation,
  filterToQuery: WorkFilter => Query) {

  lazy val independentFilters: List[WorkFilter] =
    filterSets.getOrElse(FilterCategory.Independent, List())
  lazy val aggregationDependentFilters: List[WorkFilter] =
    filterSets.getOrElse(FilterCategory.AggregationDependent, List())

  lazy val aggregations: List[AbstractAggregation] = aggregationRequests.map {
    aggReq =>
      val agg = requestToAggregation(aggReq)
      pairedFilter(aggReq) match {
        case Some(filter) =>
          agg.subAggregations(
            FilterAggregation(
              "filtered",
              boolQuery.filter {
                aggregationDependentFilters
                  .filterNot(_ == filter)
                  .map(filterToQuery)
              }
            ))
        case _ => agg
      }
  }

  private lazy val filterSets: Map[FilterCategory, List[WorkFilter]] =
    filters.groupBy {
      pairedAggregationRequest(_) match {
        case Some(aggregationRequest)
            if aggregationRequests contains aggregationRequest =>
          FilterCategory.AggregationDependent
        case _ => FilterCategory.Independent
      }
    }

  private def pairedFilter(
    aggregationRequest: AggregationRequest): Option[WorkFilter] =
    aggregationDependentFilters.find {
      pairedAggregationRequest(_) match {
        case Some(agg) => agg == aggregationRequest
        case None      => false
      }
    }

  // This pattern matching defines the pairings of filters <-> aggregations
  private def pairedAggregationRequest(
    filter: WorkFilter): Option[AggregationRequest] = filter match {
    case _: ItemLocationTypeFilter => None
    case _: WorkTypeFilter         => Some(AggregationRequest.WorkType)
    case _: DateRangeFilter        => None
    case IdentifiedWorkFilter      => None
    case _: LanguageFilter         => Some(AggregationRequest.Language)
    case _: GenreFilter            => Some(AggregationRequest.Genre)
    case _: SubjectFilter          => Some(AggregationRequest.Subject)
    case _: LicenseFilter          => Some(AggregationRequest.License)
  }

  private sealed trait FilterCategory
  private object FilterCategory {
    case object Independent extends FilterCategory
    case object AggregationDependent extends FilterCategory
  }
}
