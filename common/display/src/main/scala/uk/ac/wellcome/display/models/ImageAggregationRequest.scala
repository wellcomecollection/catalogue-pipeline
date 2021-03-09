package uk.ac.wellcome.display.models

sealed trait ImageAggregationRequest

object ImageAggregationRequest {
  case object License extends ImageAggregationRequest
  case object SourceContributors extends ImageAggregationRequest
}
