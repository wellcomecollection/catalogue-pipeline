package uk.ac.wellcome.models.work.internal

case class ProductionEvent[+Id](
  label: String,
  places: List[Place[Id]],
  agents: List[AbstractAgent[Id]],
  dates: List[Period[Id]],
  function: Option[Concept[Id]],
  ontologyType: String = "ProductionEvent"
)
