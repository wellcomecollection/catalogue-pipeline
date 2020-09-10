package uk.ac.wellcome.models.work.internal

case class ProductionEvent[+State](
  label: String,
  places: List[Place[State]],
  agents: List[AbstractAgent[State]],
  dates: List[Period[State]],
  function: Option[Concept[State]],
  ontologyType: String = "ProductionEvent"
)
