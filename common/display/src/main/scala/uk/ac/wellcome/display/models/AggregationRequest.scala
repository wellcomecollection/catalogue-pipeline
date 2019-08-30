package uk.ac.wellcome.display.models

case class InvalidAggregationRequest(key: String)

sealed trait AggregationRequest
final case class WorkTypeAggregationRequest() extends AggregationRequest

object AggregationRequest {
  def apply(
    str: String): Either[InvalidAggregationRequest, AggregationRequest] =
    str match {
      case "workType" => Right(WorkTypeAggregationRequest())
      case _          => Left(InvalidAggregationRequest(str))
    }
}
