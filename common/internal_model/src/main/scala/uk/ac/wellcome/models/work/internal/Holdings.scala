package uk.ac.wellcome.models.work.internal

case class Holdings(
  description: Option[String] = None,
  note: Option[String] = None,
  enumeration: List[String],
  locations: List[Location] = Nil
)
