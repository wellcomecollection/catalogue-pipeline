package uk.ac.wellcome.platform.api.services

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.common.Operator
import com.sksamuel.elastic4s.requests.searches.queries.matches.{
  FieldWithOptionalBoost,
  MatchQuery,
  MultiMatchQuery,
  MultiMatchQueryBuilderType
}
import com.sksamuel.elastic4s.requests.searches.queries.{
  BoolQuery,
  ConstantScore,
  Query,
  SimpleQueryStringFlag,
  SimpleStringQuery
}
import uk.ac.wellcome.platform.api.models.SearchQueryType.{
  MSMBoostUsingAndOperator,
  ScoringTiers
}
import uk.ac.wellcome.platform.api.models.{SearchQuery}
import uk.ac.wellcome.platform.api.services.QueryDefaults.{
  defaultBoostedFields,
  defaultMSM
}

case class ElasticsearchQueryBuilder(searchQuery: SearchQuery) {
  lazy val query: BoolQuery = searchQuery.queryType match {
    case MSMBoostUsingAndOperator =>
      MSMBoostUsingAndOperatorQuery(searchQuery.query).elasticQuery
    case ScoringTiers => ScoringTiersQuery(searchQuery.query).elasticQuery
  }
}

object QueryDefaults {
  val defaultMSM = "60%"
  val defaultBoostedFields: Seq[(String, Option[Double])] = Seq(
    ("data.title", Some(9.0)),
    // Because subjects and genres have been indexed differently
    // We need to query them slightly differently
    // TODO: (jamesgorrie) think of a more sustainable way of doing this
    // maybe having a just a list of terms that we use terms queries to query against,
    // and then have more structured data underlying
    ("subjects.agent.concepts.agent.label", Some(8.0)),
    ("genres.concepts.agent.label", Some(8.0)),
    ("data.description", Some(3.0)),
    ("data.contributors.*", Some(2.0)),
    ("data.alternativeTitles", None),
    ("data.physicalDescription", None),
    ("data.lettering", None),
    ("data.production.*.label", None),
    ("data.language.label", None),
    ("data.edition", None),
    // Identifiers
    ("canonicalId", None),
    ("sourceIdentifier.value", None),
    ("data.otherIdentifiers.value", None),
    ("data.items.canonicalId", None),
    ("data.items.sourceIdentifier.value", None),
    ("data.items.otherIdentifiers.value", None),
  )
}

sealed trait ElasticsearchQuery {
  val q: String
  val elasticQuery: Query
}

final case class MSMBoostUsingAndOperatorQuery(q: String)
    extends ElasticsearchQuery {
  lazy val elasticQuery = must(
    SimpleStringQuery(
      q,
      fields = defaultBoostedFields,
      lenient = Some(true),
      minimumShouldMatch = Some(defaultMSM),
      operator = Some("AND"),
      // PHRASE is the only syntax that researchers know and understand, so we use this exclusively
      // so as not to have unexpected results returned when using simple query string syntax.
      // See: https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-simple-query-string-query.html#simple-query-string-syntax
      flags = Seq(SimpleQueryStringFlag.PHRASE)
    ))
}

final case class TitleQuery(q: String) extends ElasticsearchQuery {
  lazy val elasticQuery =
    MatchQuery(
      field = "title.english",
      value = q,
      operator = Some(Operator.And))
}

final case class GenreQuery(q: String) extends ElasticsearchQuery {
  lazy val elasticQuery =
    MatchQuery(
      field = "genres.concepts.agent.label",
      value = q,
      operator = Some(Operator.And))
}

final case class SubjectQuery(q: String) extends ElasticsearchQuery {
  lazy val elasticQuery =
    MatchQuery(
      field = "subjects.agent.concepts.agent.label",
      value = q,
      operator = Some(Operator.And))
}

final case class ScoringTiersQuery(q: String) extends ElasticsearchQuery {
  import QueryDefaults._

  val fields = defaultBoostedFields map {
    case (field, boost) =>
      FieldWithOptionalBoost(field = field, boost = boost)
  }

  val baseQuery = MultiMatchQuery(
    text = q,
    fields = fields,
    minimumShouldMatch = Some(defaultMSM),
    `type` = Some(MultiMatchQueryBuilderType.CROSS_FIELDS)
  )

  lazy val elasticQuery = BoolQuery(
    should = Seq(
      ConstantScore(query = TitleQuery(q).elasticQuery, boost = Some(2000)),
      ConstantScore(query = GenreQuery(q).elasticQuery, boost = Some(1000)),
      ConstantScore(query = SubjectQuery(q).elasticQuery, boost = Some(1000)),
      baseQuery
    ))
}
