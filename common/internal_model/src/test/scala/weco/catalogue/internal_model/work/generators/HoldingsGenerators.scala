package weco.catalogue.internal_model.work.generators

import weco.catalogue.internal_model.generators.LocationGenerators
import weco.catalogue.internal_model.work.Holdings

trait HoldingsGenerators extends LocationGenerators {
  def createHoldings(count: Int): List[Holdings] =
    (1 to count).map {
      _ =>
        Holdings(
          note = chooseFrom(None, Some(randomAlphanumeric())),
          enumeration = collectionOf(min = 0, max = 10) {
            randomAlphanumeric()
          }.toList,
          location = chooseFrom(None, Some(createPhysicalLocation))
        )
    }.toList
}
