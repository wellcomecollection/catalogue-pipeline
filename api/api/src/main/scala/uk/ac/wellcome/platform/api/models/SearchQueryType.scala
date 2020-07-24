package uk.ac.wellcome.platform.api.models

import com.sksamuel.elastic4s.requests.searches.queries.BoolQuery
import uk.ac.wellcome.platform.api.elasticsearch.{
  WorksMultiMatcher,
  WorksPhraserBeam
}

sealed trait SearchQueryType {
  import uk.ac.wellcome.platform.api.models.SearchQueryType._

  // This is because the `this` is the actual simpleton so gets `$`
  // appended to the `simpleName`. Not great, but contained, so meh.
  // As this changes quite often it felt worth doing.
  final val name = this.getClass.getSimpleName.split("\\$").last

  def toEsQuery(q: String): BoolQuery = this match {
    case PhraserBeam  => WorksPhraserBeam(q)
    case MultiMatcher => WorksMultiMatcher(q)
  }
}
object SearchQueryType {
  val default = PhraserBeam
  // These are the queries that we are surfacing to the frontend to be able to select which one they want to run.
  // You'll need to change the `allowableValues` in `uk.ac.wellcome.platform.api.swagger.SwaggerDocs`
  // when changing these as they can't be read there as they need to be constant.
  val allowed = List(MultiMatcher, PhraserBeam)

  final case object MultiMatcher extends SearchQueryType
  final case object PhraserBeam extends SearchQueryType
}
