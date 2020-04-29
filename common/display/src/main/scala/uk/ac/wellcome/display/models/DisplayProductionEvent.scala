package uk.ac.wellcome.display.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.models.work.internal._

@Schema(
  name = "ProductionEvent",
  description =
    "An event contributing to the production, publishing or distribution of a work."
)
case class DisplayProductionEvent(
  @Schema label: String,
  @Schema places: List[DisplayPlace],
  @Schema agents: List[DisplayAbstractAgent],
  @Schema dates: List[DisplayPeriod],
  @Schema(
    `type` = "uk.ac.wellcome.display.models.DisplayAbstractConcept"
  ) function: Option[DisplayAbstractConcept],
  @JsonKey("type") @Schema(name = "type") ontologyType: String =
    "ProductionEvent"
)

object DisplayProductionEvent {
  def apply(productionEvent: ProductionEvent[Minted],
            includesIdentifiers: Boolean): DisplayProductionEvent = {
    DisplayProductionEvent(
      label = productionEvent.label,
      places = productionEvent.places.map { DisplayPlace(_) },
      agents = productionEvent.agents.map {
        DisplayAbstractAgent(_, includesIdentifiers = includesIdentifiers)
      },
      dates = productionEvent.dates.map { DisplayPeriod(_) },
      function = productionEvent.function.map { concept =>
        DisplayConcept(label = concept.label)
      }
    )
  }
}
