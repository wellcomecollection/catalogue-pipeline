package uk.ac.wellcome.platform.api.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.common.Operator.{AND, OR}
import com.sksamuel.elastic4s.requests.searches.queries.BoolQuery
import com.sksamuel.elastic4s.requests.searches.queries.matches.MultiMatchQueryBuilderType.BEST_FIELDS
import com.sksamuel.elastic4s.requests.searches.queries.matches.{
  FieldWithOptionalBoost,
  MultiMatchQuery
}
import uk.ac.wellcome.elasticsearch.WorksAnalysis.whitespaceAnalyzer

case object WorksMultiMatcher {
  def apply(q: String): BoolQuery = {
    boolQuery()
      .should(
        MultiMatchQuery(
          q,
          `type` = Some(BEST_FIELDS),
          operator = Some(OR),
          analyzer = Some(whitespaceAnalyzer.name),
          fields = Seq(
            ("canonicalId", Some(1000)),
            ("sourceIdentifier.value", Some(1000)),
            ("data.otherIdentifiers.value", Some(1000)),
            ("data.items.id.canonicalId", Some(1000)),
            ("data.items.id.sourceIdentifier.value", Some(1000)),
            ("data.items.id.otherIdentifiers.value", Some(1000)),
          ).map(f => FieldWithOptionalBoost(f._1, f._2.map(_.toDouble)))
        ),
        prefixQuery("data.title.keyword", q).boost(1000),
        MultiMatchQuery(
          q,
          `type` = Some(BEST_FIELDS),
          operator = Some(AND),
          fields = Seq(
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
          ).map(f => FieldWithOptionalBoost(f._1, f._2.map(_.toDouble)))
        )
      )
      .minimumShouldMatch(1)
  }
}
