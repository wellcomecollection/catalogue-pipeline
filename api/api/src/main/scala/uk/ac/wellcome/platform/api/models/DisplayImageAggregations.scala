package uk.ac.wellcome.platform.api.models

import io.circe.generic.extras.semiauto._
import io.circe.Encoder
import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.display.models._
import uk.ac.wellcome.display.json.DisplayJsonUtil._
import weco.catalogue.internal_model.identifiers.IdState.Minted
import weco.catalogue.internal_model.work.{AbstractAgent, Genre}

@Schema(
  name = "Aggregations",
  description = "A map containing the requested aggregations."
)
case class DisplayImageAggregations(
  @Schema(
    description = "License aggregation on a set of results."
  ) license: Option[DisplayAggregation[DisplayLicense]],
  @Schema(
    description = "Contributor agent aggregation on a set of results."
  ) `source.contributors.agent.label`: Option[
    DisplayAggregation[DisplayAbstractAgent]] = None,
  @Schema(
    description = "Genre aggregation on a set of results."
  ) `source.genres.label`: Option[DisplayAggregation[DisplayGenre]],
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Aggregations"
)

object DisplayImageAggregations {

  implicit def encoder: Encoder[DisplayImageAggregations] =
    deriveConfiguredEncoder

  def apply(aggs: ImageAggregations): DisplayImageAggregations =
    DisplayImageAggregations(
      license = displayAggregation(aggs.license, DisplayLicense.apply),
      `source.contributors.agent.label` =
        displayAggregation[AbstractAgent[Minted], DisplayAbstractAgent](
          aggs.sourceContributorAgents,
          DisplayAbstractAgent(_, includesIdentifiers = false)
        ),
      `source.genres.label` = displayAggregation[Genre[Minted], DisplayGenre](
        aggs.sourceGenres,
        DisplayGenre(_, includesIdentifiers = false))
    )

  private def displayAggregation[T, D](
    maybeAgg: Option[Aggregation[T]],
    display: T => D): Option[DisplayAggregation[D]] =
    maybeAgg.map {
      DisplayAggregation(_, display)
    }
}
