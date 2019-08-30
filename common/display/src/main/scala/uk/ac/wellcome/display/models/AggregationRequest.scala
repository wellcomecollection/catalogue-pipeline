package uk.ac.wellcome.display.models

sealed trait AggregationRequest
final case class WorkTypeAggregationRequest() extends AggregationRequest

object AggregationRequest {
  def apply(str: String): Option[AggregationRequest] =
    str match {
      case "workType" => Some(WorkTypeAggregationRequest())
      case _          => None
    }
}
