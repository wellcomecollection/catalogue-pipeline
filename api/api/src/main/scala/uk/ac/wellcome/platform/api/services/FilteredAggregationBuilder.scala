package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.searches.aggs.{
  Aggregation,
  FilterAggregation
}
import com.sksamuel.elastic4s.requests.searches.queries.Query
import uk.ac.wellcome.display.models.AggregationRequest
import uk.ac.wellcome.platform.api.models._

import scala.collection.immutable._

class FilteredAggregationBuilder(
  aggregationRequests: List[AggregationRequest],
  filters: List[WorkFilter],
  requestToAggregation: AggregationRequest => Aggregation,
  filterToQuery: WorkFilter => Query) {

  lazy val independentFilters: List[WorkFilter] =
    filterSets.getOrElse(FilterCategory.Independent, List())
  lazy val aggregationDependentFilters: List[WorkFilter] =
    filterSets.getOrElse(FilterCategory.AggregationDependent, List())

  lazy val aggregations: List[Aggregation] = aggregationRequests.map { aggReq =>
    val agg = requestToAggregation(aggReq)
    pairedFilter(aggReq) match {
      case Some(filter) =>
        FilterAggregation(
          agg.name,
          boolQuery.filter {
            aggregationDependentFilters
              .filterNot(_ == filter)
              .map(filterToQuery)
          }
        ).subAggregations(agg)
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

  private def pairedAggregationRequest(
    filter: WorkFilter): Option[AggregationRequest] = filter match {
    case _: ItemLocationTypeFilter => None
    case _: WorkTypeFilter         => Some(AggregationRequest.WorkType)
    case _: DateRangeFilter        => Some(AggregationRequest.ProductionDate)
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
