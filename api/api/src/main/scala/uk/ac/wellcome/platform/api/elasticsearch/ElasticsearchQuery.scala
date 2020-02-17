package uk.ac.wellcome.platform.api.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl.{must, should}
import com.sksamuel.elastic4s.requests.common.Operator
import com.sksamuel.elastic4s.requests.searches.queries.{ConstantScore, Query}
import com.sksamuel.elastic4s.requests.searches.queries.matches.{
  FieldWithOptionalBoost,
  MatchQuery,
  MultiMatchQuery,
  MultiMatchQueryBuilderType
}

sealed trait ElasticsearchQuery {
  val q: String
  val elasticQuery: Query
}

// Queries sent to the application _should_ implement this class
// as it wraps core functionality that we wouldn't want to break.
// We haven't enforced this in the type system as we _might_ want
// to try more extreme tests.
final case class CoreQuery(q: String, shouldQueries: Seq[Query])
    extends ElasticsearchQuery {

  lazy val elasticQuery = must(
    should(
      List(BaseQuery(q).elasticQuery, IdQuery(q).elasticQuery) ++
        shouldQueries: _*
    )
  )
}

case class BaseQuery(q: String) extends ElasticsearchQuery {
  val minimumShouldMatch = "60%"
  val searchFields: Seq[(String, Option[Double])] = Seq(
    ("data.subjects.concepts.label", None),
    ("data.genres.concepts.label", None),
    ("data.contributors.agent.label", None),
    ("data.title.english", None),
    ("data.description.english", None),
    ("data.alternativeTitles.english", None),
    ("data.physicalDescription.english", None),
    ("data.production.*.label", None),
    ("data.language.label", None),
    ("data.edition", None),
    ("data.notes.content.english", None),
  )

  val fields = searchFields map {
    case (field, boost) =>
      FieldWithOptionalBoost(field = field, boost = boost)
  }

  lazy val elasticQuery = MultiMatchQuery(
    text = q,
    fields = fields,
    minimumShouldMatch = Some(minimumShouldMatch),
    `type` = Some(MultiMatchQueryBuilderType.CROSS_FIELDS)
  )
}

final case class IdQuery(q: String) extends ElasticsearchQuery {
  lazy val idFields = Seq(
    "canonicalId.text",
    "sourceIdentifier.value.text",
    "data.otherIdentifiers.value.text",
    "data.items.id.canonicalId.text",
    "data.items.id.sourceIdentifier.value.text",
    "data.items.id.otherIdentifiers.value.text",
  )
  lazy val elasticQuery =
    MultiMatchQuery(
      fields = idFields.map(FieldWithOptionalBoost(_, None)),
      text = q,
      `type` = Some(MultiMatchQueryBuilderType.CROSS_FIELDS)
    )
}

final case class TitleQuery(q: String) extends ElasticsearchQuery {
  lazy val elasticQuery =
    MatchQuery(
      field = "data.title.english",
      value = q,
      operator = Some(Operator.And))
}

final case class GenreQuery(q: String) extends ElasticsearchQuery {
  lazy val elasticQuery =
    MatchQuery(
      field = "data.genres.concepts.label",
      value = q,
      operator = Some(Operator.And))
}

final case class SubjectQuery(q: String) extends ElasticsearchQuery {
  lazy val elasticQuery =
    MatchQuery(
      field = "data.subjects.concepts.label",
      value = q,
      operator = Some(Operator.And))
}

final case class ContributorQuery(q: String) extends ElasticsearchQuery {
  lazy val elasticQuery =
    MatchQuery(
      field = "data.contributors.agent.label",
      value = q,
      operator = Some(Operator.And))
}

final case class ConstScoreQuery(q: String) extends ElasticsearchQuery {
  lazy val elasticQuery = should(
    ConstantScore(query = GenreQuery(q).elasticQuery, boost = Some(2000)),
    ConstantScore(query = SubjectQuery(q).elasticQuery, boost = Some(2000)),
    ConstantScore(query = ContributorQuery(q).elasticQuery, boost = Some(2000)),
    ConstantScore(query = TitleQuery(q).elasticQuery, boost = Some(1000)),
  )
}

final case class BoolBoostedQuery(q: String) extends ElasticsearchQuery {
  lazy val elasticQuery = should(
    must(GenreQuery(q).elasticQuery).boost(2000),
    must(SubjectQuery(q).elasticQuery).boost(2000),
    must(ContributorQuery(q).elasticQuery).boost(2000),
    must(TitleQuery(q).elasticQuery).boost(1000)
  )
}
