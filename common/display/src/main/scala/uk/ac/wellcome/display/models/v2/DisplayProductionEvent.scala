package uk.ac.wellcome.display.models.v2

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
  @Schema agents: List[DisplayAbstractAgentV2],
  @Schema dates: List[DisplayPeriod],
  @Schema(
    `type` = "uk.ac.wellcome.display.models.v2.DisplayAbstractConcept"
  ) function: Option[DisplayAbstractConcept],
  @JsonKey("type") @Schema(name = "type") ontologyType: String =
    "ProductionEvent"
)

object DisplayProductionEvent {
  def apply(productionEvent: ProductionEvent[Minted[AbstractAgent]],
            includesIdentifiers: Boolean): DisplayProductionEvent = {
    DisplayProductionEvent(
      label = productionEvent.label,
      places = productionEvent.places.map { DisplayPlace(_) },
      agents = productionEvent.agents.map {
        DisplayAbstractAgentV2(_, includesIdentifiers = includesIdentifiers)
      },
      dates = productionEvent.dates.map { DisplayPeriod(_) },
      function = productionEvent.function.map { concept: Concept =>
        DisplayConcept(label = concept.label)
      }
    )
  }
}
