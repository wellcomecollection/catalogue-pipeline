package uk.ac.wellcome.display.models

import uk.ac.wellcome.display.serialize.InvalidStringKeyException

case class InvalidAggregationStringKeyRequest(key: String)
    extends InvalidStringKeyException

case class AggregationsRequest(values: List[AggregationRequest])

sealed trait AggregationRequest

object AggregationRequest {

  case object WorkType extends AggregationRequest

  case object ProductionDate extends AggregationRequest

  case object Genre extends AggregationRequest

  case object Subject extends AggregationRequest

  case object Language extends AggregationRequest

  def apply(str: String)
    : Either[InvalidAggregationStringKeyRequest, AggregationRequest] =
    str match {
      case "workType"         => Right(WorkType)
      case "genre"            => Right(Genre)
      case "production.date"  => Right(ProductionDate)
      case "subject"          => Right(Subject)
      case "language"         => Right(Language)
      case _                  => Left(InvalidAggregationStringKeyRequest(str))
    }
}
