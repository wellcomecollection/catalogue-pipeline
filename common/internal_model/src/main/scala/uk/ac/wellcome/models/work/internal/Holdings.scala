package uk.ac.wellcome.models.work.internal

import weco.catalogue.internal_model.locations.PhysicalLocation

case class Holdings(
  note: Option[String],
  enumeration: List[String],
  locations: List[PhysicalLocation]
)
