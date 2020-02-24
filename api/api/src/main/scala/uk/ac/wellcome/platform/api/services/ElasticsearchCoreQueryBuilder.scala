package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.requests.searches.queries.BoolQuery
import uk.ac.wellcome.platform.api.elasticsearch.{BoolBoostedQuery, CoreQuery}
import uk.ac.wellcome.platform.api.models.SearchQuery
import uk.ac.wellcome.platform.api.models.SearchQueryType.{
  BoolBoosted,
  PhraserBeam
}

case class ElasticsearchCoreQueryBuilder(searchQuery: SearchQuery) {
  lazy val query: BoolQuery = searchQuery.queryType match {
    case BoolBoosted =>
      CoreQuery(
        searchQuery.query,
        BoolBoostedQuery(searchQuery.query).elasticQuery.should).elasticQuery
    case PhraserBeam => CoreQuery(searchQuery.query, Seq()).elasticQuery
  }
}
