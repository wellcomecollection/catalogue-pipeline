package uk.ac.wellcome.platform.api.models

import com.sksamuel.elastic4s.requests.searches.SearchResponse
import weco.catalogue.internal_model.identifiers.IdState.Minted
import weco.catalogue.internal_model.locations.License
import weco.catalogue.internal_model.work.{AbstractAgent, Genre}

case class ImageAggregations(
  license: Option[Aggregation[License]] = None,
  sourceContributorAgents: Option[Aggregation[AbstractAgent[Minted]]] = None,
  sourceGenres: Option[Aggregation[Genre[Minted]]] = None
)

object ImageAggregations extends ElasticAggregations {
  def apply(searchResponse: SearchResponse): Option[ImageAggregations] = {
    val e4sAggregations = searchResponse.aggregations
    if (e4sAggregations.data.nonEmpty) {
      Some(
        ImageAggregations(
          license = e4sAggregations.decodeAgg[License]("license"),
          sourceContributorAgents = e4sAggregations
            .decodeAgg[AbstractAgent[Minted]]("sourceContributorAgents"),
          sourceGenres =
            e4sAggregations.decodeAgg[Genre[Minted]]("sourceGenres")
        ))
    } else {
      None
    }
  }
}
