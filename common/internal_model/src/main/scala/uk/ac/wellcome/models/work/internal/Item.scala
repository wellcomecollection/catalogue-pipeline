package uk.ac.wellcome.models.work.internal

case class Item[+Id](
  id: Id,
  title: Option[String] = None,
  locations: List[Location] = Nil,
  ontologyType: String = "Item"
) extends HasIdState[Id]
