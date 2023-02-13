package weco.catalogue.internal_model.work

case class ProductionEvent[+State](
  label: String,
  places: List[Place[State]],
  agents: List[AbstractAgent[State]],
  dates: List[Period[State]],
  function: Option[Concept[State]] = None
)
