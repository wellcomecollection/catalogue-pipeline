package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.generators.RandomStrings
import uk.ac.wellcome.models.work.internal._

trait ProductionEventGenerators extends RandomStrings {
  def createProductionEventWith(
    function: Option[Concept[Id.Minted]] = None,
    dateLabel: Option[String] = None
  ): ProductionEvent[Id.Minted] =
    ProductionEvent(
      label = randomAlphanumeric(25),
      places = List(Place(randomAlphanumeric(10))),
      agents = List(Person(randomAlphanumeric(10))),
      dates = List(Period(dateLabel.getOrElse(randomAlphanumeric(5)))),
      function = function
    )

  def createProductionEvent: ProductionEvent[Id.Minted] =
    createProductionEventWith()

  def createProductionEventList(count: Int = 1): List[ProductionEvent[Id.Minted]] =
    (1 to count).map { _ =>
      createProductionEvent
    }.toList
}
