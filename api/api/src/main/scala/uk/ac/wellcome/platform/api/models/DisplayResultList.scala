package uk.ac.wellcome.platform.api.models

import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.display.models.{DisplayWork, WorksIncludes}
import uk.ac.wellcome.models.work.internal.IdentifiedWork

@Schema(
  name = "ResultList",
  description = "A list of things."
)
case class DisplayResultList[T <: DisplayWork](
  @Schema(description = "Number of things returned per page") pageSize: Int,
  @Schema(description = "Total number of pages in the result list") totalPages: Int,
  @Schema(description = "Total number of results in the result list") totalResults: Int,
  @Schema(description = "Aggregations included about the results list") aggregations: Option[
    DisplayAggregations],
  @Schema(description = "List of things in the current page") results: List[T]) {
  @Schema(name = "type", description = "A type of thing")
  val ontologyType: String = "ResultList"
}

case object DisplayResultList {
  def apply[T <: DisplayWork, W <: WorksIncludes](
    resultList: ResultList,
    toDisplayWork: (IdentifiedWork, W) => T,
    pageSize: Int,
    includes: W): DisplayResultList[T] =
    DisplayResultList(
      results = resultList.results.map { toDisplayWork(_, includes) },
      pageSize = pageSize,
      totalPages = Math
        .ceil(resultList.totalResults.toDouble / pageSize.toDouble)
        .toInt,
      totalResults = resultList.totalResults,
      aggregations = resultList.aggregations.map(DisplayAggregations(_))
    )
}
