package uk.ac.wellcome.platform.api.models

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

sealed trait WorkQueryType

object WorkQueryType {
  case object MSMBoostQuery extends WorkQueryType
  case object MSMBoostQueryUsingAndOperator extends WorkQueryType
  case object FieldAndTerms extends WorkQueryType
}

object WorkQueryDefaults {
  val defaultMSM = "60%"
  val defaultBoostedFields: Seq[(String, Option[Double])] = Seq(
    ("data.title", Some(9.0)),
    // Because subjects and genres have been indexed differently
    // We need to query them slightly differently
    // TODO: (jamesgorrie) think of a more sustainable way of doing this
    // maybe having a just a list of terms that we use terms queries to query against,
    // and then have more structured data underlying
    ("data.subjects.*", Some(8.0)),
    ("data.genres.label", Some(8.0)),
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

case class WorkQuery(queryString: String, queryType: WorkQueryType) {

  import WorkQueryType._
  import WorkQueryDefaults._

  def query: Query =
    queryType match {
      case MSMBoostQuery =>
        SimpleStringQuery(
          queryString,
          fields = defaultBoostedFields,
          lenient = Some(true),
          minimumShouldMatch = Some(defaultMSM),
          operator = Some("OR"),
          // PHRASE is the only syntax that researchers know and understand, so we use this exclusively
          // so as not to have unexpected results returned when using simple query string syntax.
          // See: https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-simple-query-string-query.html#simple-query-string-syntax
          flags = Seq(SimpleQueryStringFlag.PHRASE)
        )
      case MSMBoostQueryUsingAndOperator =>
        SimpleStringQuery(
          queryString,
          fields = defaultBoostedFields,
          lenient = Some(true),
          minimumShouldMatch = Some(defaultMSM),
          operator = Some("AND"),
          // PHRASE is the only syntax that researchers know and understand, so we use this exclusively
          // so as not to have unexpected results returned when using simple query string syntax.
          // See: https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-simple-query-string-query.html#simple-query-string-syntax
          flags = Seq(SimpleQueryStringFlag.PHRASE)
        )
      case FieldAndTerms => FieldAndTermsQuery(queryString).elasticQuery
    }
}

sealed trait ElasticQuery {
  val qs: String
  val elasticQuery: Query
}
case class TitleQuery(qs: String) extends ElasticQuery {
  lazy val elasticQuery =
    MatchQuery(
      field = "title.english",
      value = qs,
      operator = Some(Operator.And))
}

case class GenreQuery(qs: String) extends ElasticQuery {
  lazy val elasticQuery =
    MatchQuery(
      field = "genres.concepts.agent.label",
      value = qs,
      operator = Some(Operator.And))
}

case class SubjectQuery(qs: String) extends ElasticQuery {
  lazy val elasticQuery =
    MatchQuery(
      field = "subjects.agent.concepts.agent.label",
      value = qs,
      operator = Some(Operator.And))
}

case class FieldAndTermsQuery(qs: String) extends ElasticQuery {
  import WorkQueryDefaults._

  val fields = defaultBoostedFields map {
    case (field, boost) =>
      FieldWithOptionalBoost(field = field, boost = boost)
  }
  val baseQuery = MultiMatchQuery(
    text = qs,
    fields = fields,
    minimumShouldMatch = Some(defaultMSM),
    `type` = Some(MultiMatchQueryBuilderType.CROSS_FIELDS)
  )
  lazy val elasticQuery = BoolQuery(
    should = Seq(
      ConstantScore(query = TitleQuery(qs).elasticQuery, boost = Some(2000)),
      ConstantScore(query = GenreQuery(qs).elasticQuery, boost = Some(1000)),
      ConstantScore(query = SubjectQuery(qs).elasticQuery, boost = Some(1000)),
      baseQuery
    ))
}
