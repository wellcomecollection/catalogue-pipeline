package uk.ac.wellcome.platform.api.models

import com.sksamuel.elastic4s.requests.searches.SearchResponse
import uk.ac.wellcome.models.work.internal.IdState.Minted
import uk.ac.wellcome.models.work.internal._

case class ImageAggregations(
  license: Option[Aggregation[License]] = None,
  sourceContributors: Option[Aggregation[Contributor[Minted]]] = None,
  sourceGenres: Option[Aggregation[Genre[Minted]]] = None
)

object ImageAggregations extends ElasticAggregations {
  def apply(searchResponse: SearchResponse): Option[ImageAggregations] = {
    val e4sAggregations = searchResponse.aggregations
    if (e4sAggregations.data.nonEmpty) {
      Some(
        ImageAggregations(
          license = e4sAggregations.decodeAgg[License]("license"),
          sourceContributors = e4sAggregations.decodeAgg[Contributor[Minted]](
            "sourceContributors"),
          sourceGenres =
            e4sAggregations.decodeAgg[Genre[Minted]]("sourceGenres")
        ))
    } else {
      None
    }
  }
}
