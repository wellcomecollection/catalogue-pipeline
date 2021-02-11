package uk.ac.wellcome.platform.api.elasticsearch

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.common.Operator.{AND, OR}
import com.sksamuel.elastic4s.requests.searches.queries.BoolQuery
import com.sksamuel.elastic4s.requests.searches.queries.matches.MultiMatchQueryBuilderType.{
  BEST_FIELDS,
  CROSS_FIELDS
}
import com.sksamuel.elastic4s.requests.searches.queries.matches.{
  FieldWithOptionalBoost,
  MultiMatchQuery
}
import uk.ac.wellcome.elasticsearch.WorksAnalysis.whitespaceAnalyzer

case object WorksMultiMatcher {
  def apply(q: String): BoolQuery = {
    boolQuery()
      .should(
        // we're running elasticsearch 7.9 at the moment - upgrading to 7.11 will give us the ability to run case
        // insensitive prefixQueries, which will solve our "old and new london" problem
        // (see https://github.com/wellcomecollection/catalogue/issues/1336)
        prefixQuery("data.title.keyword", q).boost(1000),
        MultiMatchQuery(
          q,
          `type` = Some(BEST_FIELDS),
          operator = Some(OR),
          analyzer = Some(whitespaceAnalyzer.name),
          fields = Seq(
            (Some(1000), "state.canonicalId"),
            (Some(1000), "state.sourceIdentifier.value"),
            (Some(1000), "data.otherIdentifiers.value"),
            (Some(1000), "data.items.id.canonicalId"),
            (Some(1000), "data.items.id.sourceIdentifier.value"),
            (Some(1000), "data.items.id.otherIdentifiers.value"),
            (Some(1000), "data.imageData.id.canonicalId"),
            (Some(1000), "data.imageData.id.sourceIdentifier.value"),
            (Some(1000), "data.imageData.id.otherIdentifiers.value"),
          ).map(f => FieldWithOptionalBoost(f._2, f._1.map(_.toDouble)))
        ),
        MultiMatchQuery(
          q,
          `type` = Some(BEST_FIELDS),
          operator = Some(OR),
          fields = Seq(
            (Some(100), "data.title"),
            (Some(100), "data.title.english"),
            (Some(100), "data.title.shingles"),
            (Some(100), "data.alternativeTitles"),
            (None, "data.lettering"),
          ).map(f => FieldWithOptionalBoost(f._2, f._1.map(_.toDouble))),
          fuzziness=Some("AUTO")
        ),
        MultiMatchQuery(
          q,
          `type` = Some(CROSS_FIELDS),
          operator = Some(AND),
          fields = Seq(
            (Some(1000), "data.contributors.agent.label"),
            (Some(10), "data.subjects.concepts.label"),
            (Some(10), "data.genres.concepts.label"),
            (Some(10), "data.production.*.label"),
            (None, "data.description"),
            (None, "data.physicalDescription"),
            (None, "data.language.label"),
            (None, "data.edition"),
            (None, "data.notes.content"),
            (None, "data.collectionPath.path"),
            (None, "data.collectionPath.label"),
          ).map(f => FieldWithOptionalBoost(f._2, f._1.map(_.toDouble)))
        )
      )
      .minimumShouldMatch(1)
  }
}
