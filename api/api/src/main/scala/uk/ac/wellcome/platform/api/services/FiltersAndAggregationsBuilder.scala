package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.searches.aggs.{
  AbstractAggregation,
  Aggregation,
  FilterAggregation
}
import com.sksamuel.elastic4s.requests.searches.queries.Query
import uk.ac.wellcome.display.models.{
  ImageAggregationRequest,
  WorkAggregationRequest
}
import uk.ac.wellcome.platform.api.models.{
  ContributorsFilter,
  FormatFilter,
  GenreFilter,
  ImageFilter,
  ItemLocationTypeFilter,
  LanguagesFilter,
  LicenseFilter,
  SubjectFilter,
  WorkFilter
}

import scala.collection.immutable._

/** This class governs the way in which we wish to combine the filters and aggregations
  * that are specified for a search. We have a concept of "pairing" a filter and an aggregation:
  * for example, an aggregation on format is paired with a filter of a specific format.
  * If a search includes an aggregation and its paired filter, the filter is *not* applied to that
  * aggregation, but is still applied to results and to all other aggregations.
  *
  * Given a list of aggregations requests and filters, as well as functions to convert these to
  * constituents of the ES query, this class exposes:
  *
  * - `filteredAggregations`: a list of all the ES query aggregations, where those that need to be filtered
  *   now have a sub-aggregation of the filter aggregation type, named "filtered".
  * - `unpairedFilters`: a list of all of the given filters which are not paired to any of
  *   the given aggregations. These can be used as the ES query filters.
  * - `pairedFilters`: a list of all of the given filters which are paired to one
  *   of the given aggregations. These can be used as the ES post-query filters.
  */
trait FiltersAndAggregationsBuilder[Filter, AggregationRequest] {
  val aggregationRequests: List[AggregationRequest]
  val filters: List[Filter]
  val requestToAggregation: AggregationRequest => Aggregation
  val filterToQuery: Filter => Query

  def pairedAggregationRequest(filter: Filter): Option[AggregationRequest]

  lazy val unpairedFilters: List[Filter] =
    filterSets.getOrElse(FilterCategory.Unpaired, List())
  lazy val pairedFilters: List[Filter] =
    filterSets.getOrElse(FilterCategory.Paired, List())

  lazy val filteredAggregations: List[AbstractAggregation] =
    aggregationRequests.map { aggReq =>
      val agg = requestToAggregation(aggReq)
      val subFilters = pairedFilters.filterNot(pairedFilter(aggReq).contains(_))
      if (subFilters.nonEmpty) {
        agg.addSubagg(
          FilterAggregation(
            "filtered",
            boolQuery.filter { subFilters.map(filterToQuery) }
          )
        )
      } else {
        agg
      }
    }

  private lazy val filterSets: Map[FilterCategory, List[Filter]] =
    filters.groupBy {
      pairedAggregationRequest(_) match {
        case Some(aggregationRequest)
            if aggregationRequests contains aggregationRequest =>
          FilterCategory.Paired
        case _ => FilterCategory.Unpaired
      }
    }

  private def pairedFilter(
    aggregationRequest: AggregationRequest): Option[Filter] =
    pairedFilters.find {
      pairedAggregationRequest(_) match {
        case Some(agg) => agg == aggregationRequest
        case None      => false
      }
    }

  private sealed trait FilterCategory
  private object FilterCategory {
    case object Unpaired extends FilterCategory
    case object Paired extends FilterCategory
  }
}

class WorkFiltersAndAggregationsBuilder(
  val aggregationRequests: List[WorkAggregationRequest],
  val filters: List[WorkFilter],
  val requestToAggregation: WorkAggregationRequest => Aggregation,
  val filterToQuery: WorkFilter => Query
) extends FiltersAndAggregationsBuilder[WorkFilter, WorkAggregationRequest]{

  override def pairedAggregationRequest(filter: WorkFilter): Option[WorkAggregationRequest] =
    filter match {
      case _: FormatFilter       => Some(WorkAggregationRequest.Format)
      case _: LanguagesFilter    => Some(WorkAggregationRequest.Languages)
      case _: GenreFilter        => Some(WorkAggregationRequest.Genre)
      case _: SubjectFilter      => Some(WorkAggregationRequest.Subject)
      case _: ContributorsFilter => Some(WorkAggregationRequest.Contributor)
      case _: LicenseFilter      => Some(WorkAggregationRequest.License)
      case _: ItemLocationTypeFilter =>
        Some(WorkAggregationRequest.ItemLocationType)
      case _ => None
    }
}

class ImageFiltersAndAggregationsBuilder(
  val aggregationRequests: List[ImageAggregationRequest],
  val filters: List[ImageFilter],
  val requestToAggregation: ImageAggregationRequest => Aggregation,
  val filterToQuery: ImageFilter => Query
) extends FiltersAndAggregationsBuilder[ImageFilter, ImageAggregationRequest] {

  override def pairedAggregationRequest(filter: ImageFilter): Option[ImageAggregationRequest] =
    filter match {
      case _: LicenseFilter => Some(ImageAggregationRequest.License)
      case _ => None
    }
}
