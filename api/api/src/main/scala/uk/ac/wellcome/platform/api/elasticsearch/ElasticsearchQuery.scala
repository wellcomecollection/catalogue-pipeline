package uk.ac.wellcome.platform.api.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl.{must, should}
import com.sksamuel.elastic4s.requests.common.Operator
import com.sksamuel.elastic4s.requests.searches.queries.Query
import com.sksamuel.elastic4s.requests.searches.queries.matches.{
  FieldWithOptionalBoost,
  MatchQuery,
  MultiMatchQuery,
  MultiMatchQueryBuilderType
}

trait BigT {
  val q: String
  val elasticQuery: Query
}

sealed trait ElasticsearchQuery extends BigT {
  val q: String
  val elasticQuery: Query
}

/**
  * Queries sent to the application _should_ implement this class
  * as it wraps core functionality that we wouldn't want to break.
  * We haven't enforced this in the type system as we _might_ want
  * to try more extreme tests.
  */
final case class CoreQuery(q: String, shouldQueries: Seq[Query])
    extends ElasticsearchQuery {

  lazy val elasticQuery = must(
    should(
      List(BaseOrQuery(q).elasticQuery, IdQuery(q).elasticQuery) ++
        shouldQueries: _*
    )
  )
}

/**
  * The `BaseAndQuery` & `BaseOrQuery` are almost identical, but we use the AND operator and a double boost on the
  * `BaseAndQuery` as AND should always score higher.
  */
case class BaseOrQuery(q: String) extends ElasticsearchQuery {
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
    `type` = Some(MultiMatchQueryBuilderType.CROSS_FIELDS),
    operator = Some(Operator.OR)
  )
}

case class BaseAndQuery(q: String) extends ElasticsearchQuery {
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
    `type` = Some(MultiMatchQueryBuilderType.CROSS_FIELDS),
    operator = Some(Operator.AND),
    boost = Some(2) // Double the OR query
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

final case class TitlePhraseQuery(q: String) extends ElasticsearchQuery {
  lazy val elasticQuery =
    MultiMatchQuery(
      text = q,
      `type` = Some(MultiMatchQueryBuilderType.PHRASE),
      fields = Seq(
        FieldWithOptionalBoost("data.title.keyword", Some(5000)),
        FieldWithOptionalBoost("data.title.english", Some(2000))
      )
    )
}

final case class SubjectsPhraseQuery(q: String) extends ElasticsearchQuery {
  lazy val elasticQuery =
    MultiMatchQuery(
      text = q,
      `type` = Some(MultiMatchQueryBuilderType.PHRASE),
      fields = Seq(
        FieldWithOptionalBoost(
          "data.subjects.concepts.label.keyword",
          Some(5000)),
        FieldWithOptionalBoost("data.subjects.concepts.label", Some(2000))
      )
    )
}

final case class GenresPhraseQuery(q: String) extends ElasticsearchQuery {
  lazy val elasticQuery =
    MultiMatchQuery(
      text = q,
      `type` = Some(MultiMatchQueryBuilderType.PHRASE),
      fields = Seq(
        FieldWithOptionalBoost(
          "data.genres.concepts.label.keyword",
          Some(5000)),
        FieldWithOptionalBoost("data.genres.concepts.label", Some(2000))
      )
    )
}

final case class ContributorsPhraseQuery(q: String) extends ElasticsearchQuery {
  lazy val elasticQuery =
    MultiMatchQuery(
      text = q,
      `type` = Some(MultiMatchQueryBuilderType.PHRASE),
      fields = Seq(
        FieldWithOptionalBoost(
          "data.contributors.agent.label.keyword",
          Some(5000)),
        FieldWithOptionalBoost("data.contributors.agent.label", Some(2000))
      )
    )
}

final case class PhraseMatchQuery(q: String) extends ElasticsearchQuery {
  lazy val elasticQuery = should(
    TitlePhraseQuery(q).elasticQuery,
    GenresPhraseQuery(q).elasticQuery,
    SubjectsPhraseQuery(q).elasticQuery,
    ContributorsPhraseQuery(q).elasticQuery,
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
