package uk.ac.wellcome.platform.api.models

import com.sksamuel.elastic4s.requests.searches.queries.{Query, QueryBuilderFn}
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.Encoder
import uk.ac.wellcome.display.json.DisplayJsonUtil._

case class SearchTemplates(templates: List[SearchTemplate])
object SearchTemplates {
  implicit val encoder: Encoder[SearchTemplates] =
    deriveConfiguredEncoder
}

case class SearchTemplate(id: String, index: String, query: String)

object SearchTemplate {
  def apply(id: String, index: String, query: Query): SearchTemplate =
    SearchTemplate(id, index, QueryBuilderFn(query).string())

  implicit val encoder: Encoder[SearchTemplate] =
    deriveConfiguredEncoder
}
