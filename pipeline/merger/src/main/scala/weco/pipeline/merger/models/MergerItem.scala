package weco.pipeline.merger.models

import cats.data.NonEmptyList
import weco.catalogue.internal_model.locations.{
  DigitalLocation,
  License,
  LocationType,
  PhysicalLocation
}
import weco.catalogue.internal_model.work.WorkState.Identified
import weco.catalogue.internal_model.work.{Item, Work}

case class MergerItem[State](item: Item[State]) {
  def appendLocationsFrom(
    sources: NonEmptyList[Work[Identified]]
  ): Item[State] =
    item.copy(
      locations = item.locations ++ sources.toList
        .flatMap(_.data.items)
        .flatMap(_.locations)
    )

  def isAvailableOnline: Boolean = item.locations.exists(
    _.locationType == LocationType.OnlineResource
  )
}
