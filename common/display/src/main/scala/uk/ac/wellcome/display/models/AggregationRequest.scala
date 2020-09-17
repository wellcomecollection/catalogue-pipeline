package uk.ac.wellcome.display.models

sealed trait AggregationRequest

object AggregationRequest {

  case object Format extends AggregationRequest

  case object ProductionDate extends AggregationRequest

  case object Genre extends AggregationRequest

  case object Subject extends AggregationRequest

  case object Language extends AggregationRequest

  case object License extends AggregationRequest
}
