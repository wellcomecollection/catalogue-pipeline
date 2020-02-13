package uk.ac.wellcome.platform.api.models

sealed trait SearchQueryType {
  // This is because the `this` is the actual simpleton so gets `$`
  // appended to the `simpleName`. Not great, but contained, so meh.
  // As this changes quite often it felt worth doing.
  final val name = this.getClass.getSimpleName.split("\\$").last
}
object SearchQueryType {
  val default = ConstScore
  // These are the queries that we are surfacing to the frontend to be able to select which one they want to run.
  // You'll need to change the `allowableValues` in `uk.ac.wellcome.platform.api.swagger.SwaggerDocs`
  // when changing these as they can't be read there as they need to be constant.
  val allowed = List(ConstScore, BoolBoosted)
  final case object ConstScore extends SearchQueryType
  final case object BoolBoosted extends SearchQueryType

  // These are used more for internal testing
  final case object Core extends SearchQueryType
}
