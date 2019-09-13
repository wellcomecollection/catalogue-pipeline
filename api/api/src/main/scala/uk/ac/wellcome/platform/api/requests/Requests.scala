package uk.ac.wellcome.platform.api.requests

import java.time.LocalDate

import com.twitter.finagle.http.Request
import com.twitter.finatra.request.{QueryParam, RouteParam}
import com.twitter.finatra.validation.{Max, Min}
import uk.ac.wellcome.display.models.{
  AggregationsRequest,
  SortsRequest,
  WorksIncludes,
}

sealed trait ApiRequest {
  val request: Request
}

case class MultipleResultsRequest(
  @Min(1) @QueryParam page: Int = 1,
  @Min(1) @Max(100) @QueryParam pageSize: Option[Int],
  @QueryParam include: Option[WorksIncludes],
  @QueryParam query: Option[String],
  @QueryParam workType: Option[String],
  @QueryParam("items.locations.locationType") itemLocationType: Option[String],
  @QueryParam("production.dates.from") productionDateFrom: Option[LocalDate],
  @QueryParam("production.dates.to") productionDateTo: Option[LocalDate],
  @QueryParam() aggregations: Option[AggregationsRequest],
  @QueryParam() sort: Option[SortsRequest],
  @QueryParam _index: Option[String],
  @QueryParam _queryType: Option[String],
  request: Request
)
case class SingleWorkRequest(
  @RouteParam id: String,
  @QueryParam include: Option[WorksIncludes],
  @QueryParam _index: Option[String],
  request: Request
)
