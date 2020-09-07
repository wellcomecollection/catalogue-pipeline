package uk.ac.wellcome.platform.api.models

import com.sksamuel.elastic4s.requests.searches.queries.{Query, QueryBuilderFn}
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.Encoder
import uk.ac.wellcome.display.json.DisplayJsonUtil._

case class SearchTemplate(id: String, index: String, query: String)

object SearchTemplate {
  def apply(id: String, index: String, query: Query): SearchTemplate =
    SearchTemplate(id, index, QueryBuilderFn(query).string())

  implicit val encoder: Encoder[SearchTemplate] =
    deriveConfiguredEncoder
}

// This is to return the search templates in the format of
// { "templates": [...] }
case class SearchTemplateResponse(templates: List[SearchTemplate])
object SearchTemplateResponse {
  implicit val encoder: Encoder[SearchTemplateResponse] =
    deriveConfiguredEncoder
}
