package uk.ac.wellcome.platform.api.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl.{moreLikeThisQuery, must, should}
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.requests.common.{DocumentRef, Operator}
import com.sksamuel.elastic4s.requests.searches.queries.{BoolQuery, Query}
import com.sksamuel.elastic4s.requests.searches.queries.matches.{
  FieldWithOptionalBoost,
  MatchQuery,
  MultiMatchQuery,
  MultiMatchQueryBuilderType
}
import uk.ac.wellcome.platform.api.models.SearchQuery
import uk.ac.wellcome.platform.api.models.SearchQueryType.{
  BoolBoosted,
  PhraserBeam
}

/**
  * We have `ComboQuery`s and `PartialQuery`s.
  *
  * `ComboQuery`s are what we use in the application as they're a
  * whole set of `PartialQuery`s stuck together, potentially with
  * different boosting etc.
  *
  * These are all quite hard to test _effectively_, as TF/IDF and
  * all the other amazing Elastic stuff means we land up massaging
  * the test data to eventually work or leaving the tests out.
  *
  * We do have core functionality tests that will run on all available
  * `ComboQuery` but will use reporting to let us know how they are
  * working for the public.
  */
sealed trait ElasticsearchQuery {
  val q: String
  val elasticQuery: Query
}

trait ElasticsearchPartialQuery extends ElasticsearchQuery
trait ElasticsearchComboQuery extends ElasticsearchQuery {
  val elasticQuery: BoolQuery
}
object ElasticsearchComboQuery {
  def apply(searchQuery: SearchQuery): ElasticsearchComboQuery =
    searchQuery.queryType match {
      case BoolBoosted =>
        BoolBoostersQuery(searchQuery.query)
      case PhraserBeam => PhraserBeamQuery(searchQuery.query)
    }
}

object QueryConfig {
  val baseWorksFields = Seq(
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
    ("data.collectionPath.path", None),
    ("data.collectionPath.label", None),
  )

  val baseImagesFields = Seq(
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
    ("data.collectionPath.path", None),
    ("data.collectionPath.label", None),
  ).flatMap {
    case (field, boost) =>
      Seq(
        (s"source.canonicalWork.$field", boost),
        (s"source.redirectedWork.$field", boost))
  }
}

case class BoolBoostersQuery(q: String) extends ElasticsearchComboQuery {
  val elasticQuery =
    CoreWorksQuery(q, BoolBoostedQuery(q).elasticQuery.should).elasticQuery
}

case class PhraserBeamQuery(q: String) extends ElasticsearchComboQuery {
  val elasticQuery =
    CoreWorksQuery(
      q,
      PhraseMatchQuery(q).elasticQuery.should ++
        Seq(BaseAndQuery(q, QueryConfig.baseWorksFields).elasticQuery)).elasticQuery
}

case class CoreImagesQuery(q: String) extends ElasticsearchComboQuery {
  val elasticQuery = must(
    should(
      List(
        BaseOrQuery(q, QueryConfig.baseImagesFields).elasticQuery,
        ImageIdQuery(q).elasticQuery
      )
    )
  )
}

/**
  * `ComboQuery`s _should_ implement this class as it wraps core
  * functionality that we wouldn't want to break.
  *
  * We haven't enforced this in the type system as we _might_ want
  * to try more extreme tests that don't implement this.
  */
final case class CoreWorksQuery(q: String, shouldQueries: Seq[Query])
    extends ElasticsearchPartialQuery {

  lazy val elasticQuery = must(
    should(
      List(
        BaseOrQuery(q, QueryConfig.baseWorksFields).elasticQuery,
        WorkIdQuery(q).elasticQuery) ++
        shouldQueries: _*
    )
  )
}

/**
  * The `BaseAndQuery` & `BaseOrQuery` are almost identical,
  * but we use the AND operator and a double boost on the
  * `BaseAndQuery` as AND should always score higher.
  */
case class BaseOrQuery(q: String, searchFields: Seq[(String, Option[Double])])
    extends ElasticsearchPartialQuery {
  val minimumShouldMatch = "60%"
  val fields = searchFields map {
    case (field, boost) =>
      FieldWithOptionalBoost(field = field, boost = boost)
  }

  lazy val elasticQuery: MultiMatchQuery = MultiMatchQuery(
    text = q,
    fields = fields,
    minimumShouldMatch = Some(minimumShouldMatch),
    `type` = Some(MultiMatchQueryBuilderType.CROSS_FIELDS),
    operator = Some(Operator.OR)
  )
}

case class BaseAndQuery(q: String, searchFields: Seq[(String, Option[Double])])
    extends ElasticsearchPartialQuery {
  val minimumShouldMatch = "60%"

  val fields = searchFields map {
    case (field, boost) =>
      FieldWithOptionalBoost(field = field, boost = boost)
  }

  lazy val elasticQuery: MultiMatchQuery = MultiMatchQuery(
    text = q,
    fields = fields,
    minimumShouldMatch = Some(minimumShouldMatch),
    `type` = Some(MultiMatchQueryBuilderType.CROSS_FIELDS),
    operator = Some(Operator.AND),
    boost = Some(2) // Double the OR query
  )
}

final case class WorkIdQuery(q: String) extends ElasticsearchPartialQuery {
  val idFields = Seq(
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

final case class ImageIdQuery(q: String) extends ElasticsearchPartialQuery {
  val idFields = Seq(
    "id.canonicalId.text",
    "id.sourceIdentifier.value.text",
    "parentWork.text"
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

final case class ImageSimilarityQuery(q: String, index: Index)
    extends ElasticsearchQuery {
  private final val lshFields = List("inferredData.lshEncodedFeatures")
  private lazy val documentRef = DocumentRef(index, q)

  lazy val elasticQuery =
    moreLikeThisQuery(lshFields)
      .likeDocs(List(documentRef))
      .copy(
        minTermFreq = Some(1),
        maxQueryTerms = Some(1000),
        minShouldMatch = Some("1")
      )
}
