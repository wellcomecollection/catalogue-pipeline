package uk.ac.wellcome.platform.api.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.requests.common.DocumentRef
import com.sksamuel.elastic4s.requests.common.Operator.AND
import com.sksamuel.elastic4s.requests.searches.queries.{BoolQuery, Query}
import com.sksamuel.elastic4s.requests.searches.queries.matches.MultiMatchQueryBuilderType.CROSS_FIELDS
import com.sksamuel.elastic4s.requests.searches.queries.matches.{
  FieldWithOptionalBoost,
  MultiMatchQuery
}

case object ImagesMultiMatcher {
  def apply(q: String): BoolQuery = {
    val fields = Seq(
      "data.subjects.concepts.label",
      "data.genres.concepts.label",
      "data.contributors.agent.label",
      "data.title.english",
      "data.description.english",
      "data.alternativeTitles.english",
      "data.physicalDescription.english",
      "data.production.*.label",
      "data.language.label",
      "data.edition",
      "data.notes.content.english",
      "data.collectionPath.path",
      "data.collectionPath.label",
    ).flatMap { field =>
      Seq(s"source.canonicalWork.$field", s"source.redirectedWork.$field")
    } map (FieldWithOptionalBoost(_, None))

    val sourceWorkIdFields = Seq(
      "id.canonicalId",
      "id.sourceIdentifier.value",
      "id.otherIdentifiers.value"
    )

    val idFields = (Seq(
      "id.canonicalId",
      "id.sourceIdentifier.value",
    ) ++ sourceWorkIdFields
      .flatMap(
        subField =>
          Seq(
            s"source.canonicalWork.$subField",
            s"source.redirectedWork.$subField"
        )
      ))
      .map(FieldWithOptionalBoost(_, None))

    should(
      MultiMatchQuery(
        text = q,
        fields = fields,
        minimumShouldMatch = Some("60%"),
        `type` = Some(CROSS_FIELDS),
        operator = Some(AND),
        boost = Some(2) // Double the OR query
      ),
      MultiMatchQuery(
        fields = idFields,
        text = q,
        `type` = Some(CROSS_FIELDS)
      )
    ).minimumShouldMatch(1)
  }
}

case object ImageSimilarity {
  def blended: (String, Index) => Query =
    lshQuery("inferredData.lshEncodedFeatures", "inferredData.palette")

  def color: (String, Index) => Query =
    lshQuery("inferredData.palette")

  def features: (String, Index) => Query =
    lshQuery("inferredData.lshEncodedFeatures")

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
