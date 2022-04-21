package weco.catalogue.display_model.work

import io.circe.generic.extras.JsonKey
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.ProductionEvent

case class DisplayProductionEvent(
  label: String,
  places: List[DisplayPlace],
  agents: List[DisplayAbstractAgent],
  dates: List[DisplayPeriod],
  function: Option[DisplayAbstractConcept],
  @JsonKey("type") ontologyType: String = "ProductionEvent"
)

object DisplayProductionEvent {
  def apply(productionEvent: ProductionEvent[IdState.Minted]): DisplayProductionEvent =
    DisplayProductionEvent(
      label = productionEvent.label,
      places = productionEvent.places.map { DisplayPlace(_) },
      agents = productionEvent.agents.map {
        DisplayAbstractAgent(_, includesIdentifiers = true)
      },
      dates = productionEvent.dates.map { DisplayPeriod(_) },
      function = productionEvent.function.map { concept =>
        DisplayConcept(label = concept.label)
      }
    )
}
