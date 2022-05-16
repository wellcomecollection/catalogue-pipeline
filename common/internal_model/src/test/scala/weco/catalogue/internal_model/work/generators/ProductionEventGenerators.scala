package weco.catalogue.internal_model.work.generators

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work._
import weco.fixtures.RandomGenerators

trait ProductionEventGenerators extends RandomGenerators {
  def createProductionEventWith(
    function: Option[Concept[IdState.Minted]] = None,
    dates: List[Period[IdState.Minted]] = List(
      Period(
        id = IdState.Unidentifiable,
        label = randomAlphanumeric(5)
      )
    )
  ): ProductionEvent[IdState.Minted] =
    ProductionEvent(
      label = randomAlphanumeric(25),
      places = List(Place(randomAlphanumeric(10))),
      agents = List(Person(randomAlphanumeric(10))),
      dates = dates,
      function = function
    )

  def createProductionEvent: ProductionEvent[IdState.Minted] =
    createProductionEventWith()

  def createProductionEventList(
    count: Int = 1
  ): List[ProductionEvent[IdState.Minted]] =
    (1 to count).map { _ =>
      createProductionEvent
    }.toList
}
