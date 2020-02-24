package uk.ac.wellcome.platform.api.elasticsearch

import com.sksamuel.elastic4s.requests.searches.queries.Query

/**
  * ComboQueries are combinations of elastic queries to help with precision and recall.
  * They are neigh impossible to test without having all the data as TF/IDF and other
  * factors play a big roll in what they do.
  *
  * We test each query `ElasticsearchQuery` separately.
  *
  * These are then used by the application, and the type ensures we don't use the
  * other queries in isolation.
  */
trait ComboQuery extends BigT {
  val elasticQuery: Query
}

case class BoolBoostersQuery(q: String) extends ComboQuery {
  val elasticQuery =
    CoreQuery(q, BoolBoostedQuery(q).elasticQuery.should).elasticQuery
}
case class PhraserBeamQuery(q: String) extends ComboQuery {
  val elasticQuery =
    CoreQuery(
      q,
      PhraseMatchQuery(q).elasticQuery.should ++
        Seq(BaseAndQuery(q).elasticQuery)).elasticQuery
}
