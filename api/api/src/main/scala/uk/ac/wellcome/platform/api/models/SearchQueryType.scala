package uk.ac.wellcome.platform.api.models

sealed trait SearchQueryType {
  // This is because the `this` is the actual simpleton so gets `$`
  // appended to the `simpleName`. Not great, but contained, so meh.
  // As this changes quite often it felt worth doing
  def name = this.getClass.getSimpleName
}

object SearchQueryType {
  val default = ScoringTiers
  final case object ScoringTiers extends SearchQueryType
  final case object FixedFields extends SearchQueryType
}
