package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.requests.searches.sort.FieldSort
import com.sksamuel.elastic4s.Index

trait ElasticsearchRequestBuilder {
  def request(queryOptions: ElasticsearchQueryOptions,
              index: Index,
              scored: Boolean): SearchRequest
  val idSort: FieldSort
}
