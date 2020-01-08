package uk.ac.wellcome.platform.api.models

sealed trait SearchQueryType

object SearchQueryType {
  val default = ScoringTiers
  final case object ScoringTiers extends SearchQueryType
  final case object FixedFields extends SearchQueryType
}
