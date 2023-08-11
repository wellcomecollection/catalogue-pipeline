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

  def copyLicenceFrom(sources: Seq[Work[Identified]]): Item[State] = {
    val candidateLicences = sources
      .flatMap(_.data.items)
      .flatMap(_.locations)
      .flatMap(_.license)
      .distinct
    candidateLicences match {
      // Only copy the licence if there is no ambiguity.
      // The purpose of this is to harmonise the licences on a work when the source overrides the target
      // if there are multiple conflicting sources, then we might as well leave it as it is.
      case Seq(licence) => copyLicence(licence)
      //
      case _ =>
        item // Probably log that nothing has changed, particularly if there are more than one licence
    }
  }

  def copyLicence[T](
    newLicence: License
  ): Item[State] = {

    item.copy(
      locations = item.locations map {
        case digitalLocation: DigitalLocation =>
          digitalLocation.copy(license = Some(newLicence))
        case physicalLocation: PhysicalLocation =>
          physicalLocation.copy(license = Some(newLicence))
      }
    )
  }

  def isAvailableOnline: Boolean = item.locations.exists(
    _.locationType == LocationType.OnlineResource
  )
}
