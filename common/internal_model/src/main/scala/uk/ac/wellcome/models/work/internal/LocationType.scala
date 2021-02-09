package uk.ac.wellcome.models.work.internal

sealed trait LocationType

sealed trait PhysicalLocationType extends LocationType
sealed trait DigitalLocationType extends LocationType

object LocationType {
  case object ClosedStores extends PhysicalLocationType
  case object OpenShelves extends PhysicalLocationType
  case object OnExhibition extends PhysicalLocationType

  case object IIIFPresentationAPI extends DigitalLocationType
  case object IIIFImageAPI extends DigitalLocationType
  case object OnlineResource extends DigitalLocationType
}
