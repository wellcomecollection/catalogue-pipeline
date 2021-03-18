package weco.catalogue.internal_model.work

import weco.catalogue.internal_model.locations.PhysicalLocation

case class Holdings(
  note: Option[String],
  enumeration: List[String],
  locations: List[PhysicalLocation]
)
