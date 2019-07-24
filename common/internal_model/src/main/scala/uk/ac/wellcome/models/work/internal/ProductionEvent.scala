package uk.ac.wellcome.models.work.internal

case class ProductionEvent[+T <: IdentityState[AbstractAgent]](
  label: String,
  places: List[Place],
  agents: List[T],
  dates: List[Period],
  function: Option[Concept],
  ontologyType: String = "ProductionEvent"
) {

  def withDates(dates: List[Period]): ProductionEvent[T] = 
    ProductionEvent(label, places, agents, dates, function, ontologyType)
}
