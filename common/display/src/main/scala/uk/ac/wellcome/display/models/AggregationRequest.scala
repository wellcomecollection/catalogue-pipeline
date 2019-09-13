package uk.ac.wellcome.display.models

import uk.ac.wellcome.display.serialize.InvalidStringKeyException

case class InvalidAggregationStringKeyRequest(key: String)
    extends InvalidStringKeyException

case class AggregationsRequest(values: List[AggregationRequest])
sealed trait AggregationRequest
final case class WorkTypeAggregationRequest() extends AggregationRequest

object AggregationRequest {
  def apply(str: String)
    : Either[InvalidAggregationStringKeyRequest, AggregationRequest] =
    str match {
      case "workType" => Right(WorkTypeAggregationRequest())
      case _          => Left(InvalidAggregationStringKeyRequest(str))
    }
}
