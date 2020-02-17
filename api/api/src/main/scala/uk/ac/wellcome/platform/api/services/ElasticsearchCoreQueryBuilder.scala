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
  ConstScore
}

/**
  * The point of this builder is to make sure the app always wraps
  * any test queries in core functionality
  */
// TODO: Find a better way to compose these queries
case class ElasticsearchCoreQueryBuilder(searchQuery: SearchQuery) {
  lazy val query: BoolQuery = searchQuery.queryType match {
    case ConstScore =>
      CoreQuery(
        searchQuery.query,
        ConstScoreQuery(searchQuery.query).elasticQuery.should).elasticQuery
    case BoolBoosted =>
      CoreQuery(
        searchQuery.query,
        BoolBoostedQuery(searchQuery.query).elasticQuery.should).elasticQuery
  }
}
