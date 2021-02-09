package uk.ac.wellcome.models.work.internal

sealed trait NewLocationType

sealed trait PhysicalLocationType extends NewLocationType
sealed trait DigitalLocationType extends NewLocationType

object NewLocationType {
  case object ClosedStores extends PhysicalLocationType
  case object OpenShelves extends PhysicalLocationType
  case object OnExhibition extends PhysicalLocationType

  case object IIIFPresentationAPI extends DigitalLocationType
  case object IIIFImageAPI extends DigitalLocationType
  case object OnlineResource extends DigitalLocationType
}
