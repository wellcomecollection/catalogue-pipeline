package uk.ac.wellcome.display.models

sealed trait LocationTypeQuery { this: LocationTypeQuery =>
  def name = s"${this.getClass.getSimpleName.stripSuffix("$")}Deprecated"
}

object LocationTypeQuery {
  case object DigitalLocation extends LocationTypeQuery
  case object PhysicalLocation extends LocationTypeQuery
}
