package uk.ac.wellcome.platform.api.models

sealed trait SearchQueryType

object SearchQueryType {
  val default = MSMBoostUsingAndOperator
  final case object MSMBoostUsingAndOperator extends SearchQueryType
  final case object ScoringTiers extends SearchQueryType
}
