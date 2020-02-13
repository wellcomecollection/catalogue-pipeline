package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.requests.searches.queries.BoolQuery
import uk.ac.wellcome.platform.api.elasticsearch.{
  BoolBoostedQuery,
  ConstScoreQuery,
  CoreQuery
}
import uk.ac.wellcome.platform.api.models.SearchQuery
import uk.ac.wellcome.platform.api.models.SearchQueryType.{
  BoolBoosted,
  ConstScore,
  Core
}

case class ElasticsearchQueryBuilder(searchQuery: SearchQuery) {
  lazy val query: BoolQuery = searchQuery.queryType match {
    case Core       => CoreQuery(searchQuery.query, Seq()).elasticQuery
    case ConstScore => ConstScoreQuery(searchQuery.query).elasticQuery
    case BoolBoosted =>
      BoolBoostedQuery(searchQuery.query).elasticQuery
  }
}
