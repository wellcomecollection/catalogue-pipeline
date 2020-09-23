package uk.ac.wellcome.display.models

sealed trait LocationTypeRequest { this: LocationTypeRequest =>
  def name = this.getClass.getSimpleName.stripSuffix("$")
}
object LocationTypeRequest {
  case object DigitalLocation extends LocationTypeRequest
  case object PhysicalLocation extends LocationTypeRequest
}
