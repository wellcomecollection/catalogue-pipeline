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

/**
  * We have `ComboQuery`s and `PartialQuery`s to help us with testing.
  *
  * `ComboQuery`s are what the application use and are hard to test
  * as they have a lot of weighting around TF/IDF in them that you:
  * 1. Spend ages tuning your data in the tests for to account for TF/IDF-ness
  * 2. Don't really capture anything without using large data sets where TF/IDF is relevant
  *
  * This way we can test the `PartialQuery`s in Scala app and use reporting to
  * let us know if we're succeeding in giving people what they want.
  */
sealed trait ElasticsearchQuery {
  val q: String
  val elasticQuery: Query
}

trait ElasticsearchPartialQuery extends ElasticsearchQuery
trait ElasticsearchComboQuery extends ElasticsearchQuery

case class BoolBoostersQuery(q: String) extends ElasticsearchComboQuery {
  val elasticQuery =
    CoreQuery(q, BoolBoostedQuery(q).elasticQuery.should).elasticQuery
}

case class PhraserBeamQuery(q: String) extends ElasticsearchComboQuery {
  val elasticQuery =
    CoreQuery(
      q,
      PhraseMatchQuery(q).elasticQuery.should ++
        Seq(BaseAndQuery(q).elasticQuery)).elasticQuery
}

/**
  * `ComboQuery`s _should_ implement this class as it wraps core
  * functionality that we wouldn't want to break.
  *
  * We haven't enforced this in the type system as we _might_ want
  * to try more extreme tests that don't implement this.
  */
final case class CoreQuery(q: String, shouldQueries: Seq[Query])
    extends ElasticsearchPartialQuery {

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
case class BaseOrQuery(q: String) extends ElasticsearchPartialQuery {
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

case class BaseAndQuery(q: String) extends ElasticsearchPartialQuery {
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

final case class IdQuery(q: String) extends ElasticsearchPartialQuery {
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

final case class TitleQuery(q: String) extends ElasticsearchPartialQuery {
  lazy val elasticQuery =
    MatchQuery(
      field = "data.title.english",
      value = q,
      operator = Some(Operator.And))
}

final case class GenreQuery(q: String) extends ElasticsearchPartialQuery {
  lazy val elasticQuery =
    MatchQuery(
      field = "data.genres.concepts.label",
      value = q,
      operator = Some(Operator.And))
}

final case class SubjectQuery(q: String) extends ElasticsearchPartialQuery {
  lazy val elasticQuery =
    MatchQuery(
      field = "data.subjects.concepts.label",
      value = q,
      operator = Some(Operator.And))
}

final case class ContributorQuery(q: String) extends ElasticsearchPartialQuery {
  lazy val elasticQuery =
    MatchQuery(
      field = "data.contributors.agent.label",
      value = q,
      operator = Some(Operator.And))
}

final case class TitlePhraseQuery(q: String) extends ElasticsearchPartialQuery {
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

final case class SubjectsPhraseQuery(q: String)
    extends ElasticsearchPartialQuery {
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

final case class GenresPhraseQuery(q: String)
    extends ElasticsearchPartialQuery {
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

final case class ContributorsPhraseQuery(q: String)
    extends ElasticsearchPartialQuery {
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

final case class PhraseMatchQuery(q: String) extends ElasticsearchPartialQuery {
  lazy val elasticQuery = should(
    TitlePhraseQuery(q).elasticQuery,
    GenresPhraseQuery(q).elasticQuery,
    SubjectsPhraseQuery(q).elasticQuery,
    ContributorsPhraseQuery(q).elasticQuery,
  )
}

final case class BoolBoostedQuery(q: String) extends ElasticsearchPartialQuery {
  lazy val elasticQuery = should(
    must(GenreQuery(q).elasticQuery).boost(2000),
    must(SubjectQuery(q).elasticQuery).boost(2000),
    must(ContributorQuery(q).elasticQuery).boost(2000),
    must(TitleQuery(q).elasticQuery).boost(1000)
  )
}
