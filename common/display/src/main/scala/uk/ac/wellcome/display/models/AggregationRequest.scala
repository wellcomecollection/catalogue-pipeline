package uk.ac.wellcome.display.models

import uk.ac.wellcome.display.serialize.InvalidStringKeyException

case class InvalidAggregationStringKeyRequest(key: String)
    extends InvalidStringKeyException

case class AggregationsRequest(values: List[AggregationRequest])

sealed trait AggregationRequest

object AggregationRequest {

  case object WorkType extends AggregationRequest

  case class Date(interval: DateInterval = DateInterval.Year)
    extends AggregationRequest

  def apply(str: String)
    : Either[InvalidAggregationStringKeyRequest, AggregationRequest] =
    str match {
      case "workType" => Right(AggregationRequest.WorkType)
      case "year"     => Right(AggregationRequest.Date(interval = DateInterval.Year))
      case _          => Left(InvalidAggregationStringKeyRequest(str))
    }
}

sealed trait DateInterval

object DateInterval {
  object Year extends DateInterval
}
