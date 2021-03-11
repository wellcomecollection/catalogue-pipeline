package uk.ac.wellcome.platform.api.models

import io.circe.generic.extras.semiauto._
import io.circe.Encoder
import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.display.models._
import uk.ac.wellcome.display.json.DisplayJsonUtil._
import uk.ac.wellcome.models.work.internal.AbstractAgent
import uk.ac.wellcome.models.work.internal.IdState.Minted

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
        )
    )

  private def displayAggregation[T, D](
    maybeAgg: Option[Aggregation[T]],
    display: T => D): Option[DisplayAggregation[D]] =
    maybeAgg.map {
      DisplayAggregation(_, display)
    }
}
