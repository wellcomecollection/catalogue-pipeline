package uk.ac.wellcome.display.models

import uk.ac.wellcome.display.serialize.InvalidStringKeyException

case class InvalidAggregationStringKeyRequest(key: String)
    extends InvalidStringKeyException

case class AggregationsRequest(values: List[AggregationRequest])

sealed trait AggregationRequest

object AggregationRequest {

  case object WorkType extends AggregationRequest

  case object ProductionDates extends AggregationRequest

  def apply(str: String)
    : Either[InvalidAggregationStringKeyRequest, AggregationRequest] =
    str match {
      case "workType"         => Right(AggregationRequest.WorkType)
      case "production.dates" => Right(AggregationRequest.ProductionDates)
      case _                  => Left(InvalidAggregationStringKeyRequest(str))
    }
}
