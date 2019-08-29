package uk.ac.wellcome.display.models

sealed trait AggregationRequest
final case class WorkTypeAggregationRequest() extends AggregationRequest

object AggregationsRequest {
  def apply(strs: Array[String]): List[AggregationRequest] =
    (strs flatMap {
      case "workType" => Some(WorkTypeAggregationRequest())
      case _          => None
    }).toList

  val recognisedAggregations = List("workType")
}
