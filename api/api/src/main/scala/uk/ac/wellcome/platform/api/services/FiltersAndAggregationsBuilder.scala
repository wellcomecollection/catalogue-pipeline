package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.searches.aggs.{
  AbstractAggregation,
  Aggregation,
  FilterAggregation,
  GlobalAggregation
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
  *   now have a sub-aggregation of the filter aggregation type, named "filtered", and are in the global
  *   aggregation context so post-filtering of query results is not required.
  */
trait FiltersAndAggregationsBuilder[Filter, AggregationRequest] {
  val aggregationRequests: List[AggregationRequest]
  val filters: List[Filter]
  val requestToAggregation: AggregationRequest => Aggregation
  val filterToQuery: Filter => Query
  val searchQuery: Query

  def pairedAggregationRequests(filter: Filter): List[AggregationRequest]

  lazy val filteredAggregations: List[AbstractAggregation] =
    aggregationRequests.map { aggReq =>
      val agg = requestToAggregation(aggReq)
      pairedFilter(aggReq) match {
        case Some(paired) =>
          val subFilters = filters.filterNot(_ == paired)
          GlobalAggregation(
            // We would like to rename the aggregation here to something predictable
            // (eg "global_agg") but because it is an opaque AbstractAggregation we
            // make do with naming it the same as its parent GlobalAggregation, so that
            // the latter can be picked off when parsing in WorkAggregations
            name = agg.name,
            subaggs = Seq(
              agg.addSubagg(
                FilterAggregation(
                  "filtered",
                  boolQuery.filter {
                    searchQuery :: subFilters.map(filterToQuery)
                  }
                )
              )
            )
          )
        case _ => agg
      }
    }

  private def pairedFilter(
    aggregationRequest: AggregationRequest): Option[Filter] =
    filters.find { filter =>
      pairedAggregationRequests(filter)
        .contains(aggregationRequest)
    }
}

class WorkFiltersAndAggregationsBuilder(
  val aggregationRequests: List[WorkAggregationRequest],
  val filters: List[WorkFilter],
  val requestToAggregation: WorkAggregationRequest => Aggregation,
  val filterToQuery: WorkFilter => Query,
  val searchQuery: Query
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
      case _: SubjectFilter =>
        List(
          WorkAggregationRequest.Subject,
          WorkAggregationRequest.SubjectDeprecated)
      case _: ContributorsFilter =>
        List(
          WorkAggregationRequest.Contributor,
          WorkAggregationRequest.ContributorDeprecated)
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
  val filterToQuery: ImageFilter => Query,
  val searchQuery: Query
) extends FiltersAndAggregationsBuilder[ImageFilter, ImageAggregationRequest] {

  override def pairedAggregationRequests(
    filter: ImageFilter): List[ImageAggregationRequest] =
    filter match {
      case _: LicenseFilter => List(ImageAggregationRequest.License)
      case _                => Nil
    }
}
