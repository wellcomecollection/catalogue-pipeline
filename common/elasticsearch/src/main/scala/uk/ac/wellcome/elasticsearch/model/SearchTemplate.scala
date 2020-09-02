package uk.ac.wellcome.elasticsearch.model

import com.sksamuel.elastic4s.requests.searches.queries.Query

case class SearchTemplate(id: String, query: Query)
