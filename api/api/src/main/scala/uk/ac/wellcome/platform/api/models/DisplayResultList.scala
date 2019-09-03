package uk.ac.wellcome.platform.api.models

import io.swagger.annotations.ApiModelProperty.AccessMode
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import uk.ac.wellcome.display.models.{DisplayWork, WorksIncludes}
import uk.ac.wellcome.models.work.internal.IdentifiedWork
import uk.ac.wellcome.json.JsonUtil._

@ApiModel(
  value = "ResultList",
  description = "A list of things."
)
case class DisplayResultList[T <: DisplayWork](
  @ApiModelProperty(value = "Number of things returned per page") pageSize: Int,
  @ApiModelProperty(value = "Total number of pages in the result list") totalPages: Int,
  @ApiModelProperty(value = "Total number of results in the result list") totalResults: Int,
  @ApiModelProperty(value = "Aggregations included about the results list") aggregations: Option[
    AggregationResults] = None,
  @ApiModelProperty(value = "List of things in the current page") results: List[
    T]) {
  @ApiModelProperty(
    name = "type",
    value = "A type of thing",
    accessMode = AccessMode.READ_ONLY)
  val ontologyType: String = "ResultList"
}

case object DisplayResultList {
  def apply[T <: DisplayWork, W <: WorksIncludes](
    resultList: ResultList,
    toDisplayWork: (IdentifiedWork, W) => T,
    pageSize: Int,
    includes: W): DisplayResultList[T] = {
    println("-==================================")
    println(toJson(resultList.aggregations))
    println("-==================================")
    DisplayResultList(
      results = resultList.results.map { toDisplayWork(_, includes) },
      pageSize = pageSize,
      totalPages = Math
        .ceil(resultList.totalResults.toDouble / pageSize.toDouble)
        .toInt,
      totalResults = resultList.totalResults,
      aggregations = resultList.aggregations
    )
  }
}
