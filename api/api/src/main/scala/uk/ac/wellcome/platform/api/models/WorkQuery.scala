package uk.ac.wellcome.platform.api.models

import com.sksamuel.elastic4s.requests.searches.queries.{
  Query,
  SimpleQueryStringFlag,
  SimpleStringQuery
}

sealed trait WorkQueryType

object WorkQueryType {
  case object MSMBoostQuery extends WorkQueryType
  case object MSMBoostQueryUsingAndOperator extends WorkQueryType
}

case class WorkQuery(queryString: String, queryType: WorkQueryType) {

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

  import WorkQueryType._

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
    }
}
