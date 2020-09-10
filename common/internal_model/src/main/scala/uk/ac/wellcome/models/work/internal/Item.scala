package uk.ac.wellcome.models.work.internal

case class Item[+Id](
  id: Id,
  title: Option[String] = None,
  locationsDeprecated: List[LocationDeprecated] = Nil,
  ontologyType: String = "Item"
) extends HasIdState[Id]

object Item {

  def apply[Id >: Unidentifiable.type](
    title: Option[String],
    locationsDeprecated: List[LocationDeprecated]
  ): Item[Id] =
    Item(Unidentifiable, title, locationsDeprecated)
}
