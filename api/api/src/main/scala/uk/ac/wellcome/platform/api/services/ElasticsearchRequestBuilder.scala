package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.requests.searches.sort.FieldSort
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.requests.searches.queries.Query
import com.sksamuel.elastic4s.ElasticDsl._
import uk.ac.wellcome.platform.api.models.{DocumentFilter, SearchOptions}

trait ElasticsearchRequestBuilder {

  val idSort: FieldSort
  type Filter <: DocumentFilter

  def request(searchOptions: SearchOptions[Filter],
              index: Index,
              scored: Boolean): SearchRequest
}

object ElasticsearchRequestBuilder {

  def includesExcludesQuery(field: String,
                            includes: List[String],
                            excludes: List[String]): Query =
    (includes, excludes) match {
      case (_, Nil) =>
        termsQuery(field = field, values = includes)
      case (Nil, _) =>
        not(termsQuery(field = field, values = excludes))
      case _ =>
        must(
          termsQuery(field = field, values = includes),
          not(termsQuery(field = field, values = excludes)))
    }
}
