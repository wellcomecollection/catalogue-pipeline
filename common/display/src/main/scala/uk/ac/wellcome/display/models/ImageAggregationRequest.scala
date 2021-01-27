package uk.ac.wellcome.display.models

sealed trait ImageAggregationRequest

object ImageAggregationRequest {
  case object License extends ImageAggregationRequest
}
