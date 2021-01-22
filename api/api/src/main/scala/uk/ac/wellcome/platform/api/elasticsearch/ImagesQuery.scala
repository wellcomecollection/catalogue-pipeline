package uk.ac.wellcome.platform.api.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.requests.common.DocumentRef
import com.sksamuel.elastic4s.requests.common.Operator.{AND, OR}
import com.sksamuel.elastic4s.requests.searches.queries.{BoolQuery, Query}
import com.sksamuel.elastic4s.requests.searches.queries.matches.MultiMatchQueryBuilderType.{
  BEST_FIELDS,
  CROSS_FIELDS
}
import com.sksamuel.elastic4s.requests.searches.queries.matches.{
  FieldWithOptionalBoost,
  MultiMatchQuery
}
import uk.ac.wellcome.elasticsearch.WorksAnalysis.whitespaceAnalyzer

case object ImagesMultiMatcher {
  def apply(q: String): BoolQuery = {
    val fields = Seq(
      ("data.contributors.agent.label", Some(1000)),
      ("data.title", Some(100)),
      ("data.title.english", Some(100)),
      ("data.title.shingles", Some(100)),
      ("data.alternativeTitles", Some(100)),
      ("data.subjects.concepts.label", Some(10)),
      ("data.genres.concepts.label", Some(10)),
      ("data.production.*.label", Some(10)),
      ("data.description", None),
      ("data.physicalDescription", None),
      ("data.language.label", None),
      ("data.edition", None),
      ("data.notes.content", None),
      ("data.lettering", None),
      ("data.collectionPath.path", None),
      ("data.collectionPath.label", None),
    ) flatMap {
      case (field, boost) =>
        toWorkField(field).map(workField => (workField, boost))
    } map {
      case (field, boost) =>
        FieldWithOptionalBoost(field, boost.map(_.toDouble))
    }

    val sourceWorkIdFields = Seq(
      "id.canonicalId",
      "id.sourceIdentifier.value",
      "data.otherIdentifiers.value"
    )

    val idFields = (Seq(
      "state.canonicalId",
      "state.sourceIdentifier.value",
    ) ++ sourceWorkIdFields
      .flatMap(toWorkField))
      .map(fi => FieldWithOptionalBoost(fi, None))

    should(
      MultiMatchQuery(
        text = q,
        fields = fields,
        `type` = Some(CROSS_FIELDS),
        operator = Some(AND),
      ),
      prefixQuery("data.title.keyword", q).boost(1000),
      MultiMatchQuery(
        fields = idFields,
        text = q,
        `type` = Some(BEST_FIELDS),
        operator = Some(OR),
        analyzer = Some(whitespaceAnalyzer.name),
      ).boost(1000)
    ).minimumShouldMatch(1)
  }

  def toWorkField(field: String): Seq[String] =
    Seq(s"source.canonicalWork.$field", s"source.redirectedWork.$field")
}

case object ImageSimilarity {
  def blended: (String, Index) => Query =
    lshQuery("state.inferredData.lshEncodedFeatures", "inferredData.palette")

  def color: (String, Index) => Query =
    lshQuery("state.inferredData.palette")

  def features: (String, Index) => Query =
    lshQuery("state.inferredData.lshEncodedFeatures")

  private def lshQuery(fields: String*)(q: String, index: Index): Query = {
    val documentRef = DocumentRef(index, q)

    moreLikeThisQuery(fields)
      .likeDocs(List(documentRef))
      .copy(
        minTermFreq = Some(1),
        minDocFreq = Some(1),
        maxQueryTerms = Some(1000),
        minShouldMatch = Some("1")
      )
  }
}
