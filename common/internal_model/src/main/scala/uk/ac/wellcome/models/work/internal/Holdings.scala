package uk.ac.wellcome.models.work.internal

case class Holdings(
  note: Option[String],
  enumeration: List[String],
  locations: List[PhysicalLocation]
)
