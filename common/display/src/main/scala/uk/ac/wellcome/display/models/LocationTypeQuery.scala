package uk.ac.wellcome.display.models

sealed trait LocationTypeQuery { this: LocationTypeQuery =>
  def name = this.getClass.getSimpleName.stripSuffix("$")
}
object LocationTypeQuery {
  case object DigitalLocation extends LocationTypeQuery
  case object PhysicalLocation extends LocationTypeQuery
}
