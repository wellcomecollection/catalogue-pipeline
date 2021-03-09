package uk.ac.wellcome.models.work.internal

case class Holdings(
  description: Option[String],
  note: Option[String],
  enumeration: List[String],
  locations: List[PhysicalLocation]
)
