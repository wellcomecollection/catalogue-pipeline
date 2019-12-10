package uk.ac.wellcome.models.work.internal

case class Item(
  title: Option[String] = None,
  locations: List[Location] = Nil,
  ontologyType: String = "Item"
)
