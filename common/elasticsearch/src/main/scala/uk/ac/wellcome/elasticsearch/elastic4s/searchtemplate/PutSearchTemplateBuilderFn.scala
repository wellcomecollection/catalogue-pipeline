package uk.ac.wellcome.elasticsearch.elastic4s.searchtemplate

import com.sksamuel.elastic4s.json.{XContentBuilder, XContentFactory}
import com.sksamuel.elastic4s.requests.searches.queries.{Query, QueryBuilderFn}

object PutSearchTemplateBuilderFn {
  def apply(request: PutSearchTemplateRequest): XContentBuilder = {
    val builder = XContentFactory.jsonBuilder().startObject("script")
    builder.field("lang", "mustache")
    builder.startObject("source")
    builder.rawField("query", QueryBuilderFn(request.query))
    builder.endObject().endObject()
  }
}

case class PutSearchTemplateRequest(id: String, query: Query)
