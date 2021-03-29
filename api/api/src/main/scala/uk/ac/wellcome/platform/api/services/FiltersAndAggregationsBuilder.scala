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
  AvailabilitiesFilter,
  ContributorsFilter,
  FormatFilter,
  GenreFilter,
  ImageFilter,
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

  def pairedAggregationRequests(filter: Filter): List[AggregationRequest]

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
      pairedAggregationRequests(_) match {
        case pairedRequests
            if aggregationRequests.intersect(pairedRequests).nonEmpty =>
          FilterCategory.Paired
        case _ => FilterCategory.Unpaired
      }
    }

  private def pairedFilter(
    aggregationRequest: AggregationRequest): Option[Filter] =
    pairedFilters.find {
      pairedAggregationRequests(_)
        .contains(aggregationRequest)
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
) extends FiltersAndAggregationsBuilder[WorkFilter, WorkAggregationRequest] {

  override def pairedAggregationRequests(
    filter: WorkFilter): List[WorkAggregationRequest] =
    filter match {
      case _: FormatFilter    => List(WorkAggregationRequest.Format)
      case _: LanguagesFilter => List(WorkAggregationRequest.Languages)
      case _: GenreFilter =>
        List(
          WorkAggregationRequest.Genre,
          WorkAggregationRequest.GenreDeprecated)
      case _: SubjectFilter      => List(WorkAggregationRequest.Subject)
      case _: ContributorsFilter => List(WorkAggregationRequest.Contributor)
      case _: LicenseFilter =>
        List(
          WorkAggregationRequest.License,
          WorkAggregationRequest.LicenseDeprecated)
      case _: AvailabilitiesFilter =>
        List(WorkAggregationRequest.Availabilities)
      case _ => Nil
    }
}

class ImageFiltersAndAggregationsBuilder(
  val aggregationRequests: List[ImageAggregationRequest],
  val filters: List[ImageFilter],
  val requestToAggregation: ImageAggregationRequest => Aggregation,
  val filterToQuery: ImageFilter => Query
) extends FiltersAndAggregationsBuilder[ImageFilter, ImageAggregationRequest] {

  override def pairedAggregationRequests(
    filter: ImageFilter): List[ImageAggregationRequest] =
    filter match {
      case _: LicenseFilter => List(ImageAggregationRequest.License)
      case _                => Nil
    }
}
