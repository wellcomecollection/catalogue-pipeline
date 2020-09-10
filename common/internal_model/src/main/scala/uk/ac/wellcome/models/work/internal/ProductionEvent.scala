package uk.ac.wellcome.models.work.internal

case class ProductionEvent[+DataId](
  label: String,
  places: List[Place[DataId]],
  agents: List[AbstractAgent[DataId]],
  dates: List[Period[DataId]],
  function: Option[Concept[DataId]],
  ontologyType: String = "ProductionEvent"
)
