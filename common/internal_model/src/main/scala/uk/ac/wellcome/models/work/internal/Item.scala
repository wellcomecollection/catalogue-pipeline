package uk.ac.wellcome.models.work.internal

case class Item[+Id](
  id: Id,
  title: Option[String] = None,
  locations: List[Location] = Nil,
  ontologyType: String = "Item"
) extends HasIdState[Id]

object Item {

  def apply[Id >: Unidentifiable.type](
    title: Option[String],
    locations: List[Location]
  ): Item[Id] =
    Item(Unidentifiable, title, locations)
}
