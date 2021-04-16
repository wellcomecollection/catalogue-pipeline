package weco.catalogue.internal_model.work

import weco.catalogue.internal_model.locations.Location

case class Holdings(
  note: Option[String],
  enumeration: List[String],
  location: Option[Location]
)
