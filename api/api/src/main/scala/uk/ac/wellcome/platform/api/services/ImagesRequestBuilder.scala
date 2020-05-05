package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.requests.searches._
import com.sksamuel.elastic4s.requests.searches.sort._
import uk.ac.wellcome.platform.api.elasticsearch.CoreImagesQuery

object ImagesRequestBuilder extends ElasticsearchRequestBuilder {

  val idSort: FieldSort = fieldSort("id.canonicalId").order(SortOrder.ASC)

  def request(queryOptions: ElasticsearchQueryOptions,
              index: Index,
              scored: Boolean): SearchRequest =
    search(index)
      .query(
        queryOptions.searchQuery
          .map { q =>
            CoreImagesQuery(q.query).elasticQuery
          }
          .getOrElse(boolQuery)
      )
      .sortBy(idSort)
      .limit(queryOptions.limit)
      .from(queryOptions.from)
}
