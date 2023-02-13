package weco.catalogue.internal_model.work

// i think this is part of the culprit
case class ProductionEvent[+State](
  label: String,
  places: List[Place[State]],
  agents: List[AbstractAgent[State]],
  dates: List[Period[State]],
  function: Option[Concept[State]] = None
)
