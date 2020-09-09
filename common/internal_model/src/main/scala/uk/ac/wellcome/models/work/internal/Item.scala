package uk.ac.wellcome.models.work.internal

import IdState._

case class Item[+Id](
  id: Id,
  title: Option[String] = None,
  locations: List[LocationDeprecated] = Nil,
  ontologyType: String = "Item"
) extends HasIdState[Id]

object Item {

  def apply[Id >: Unidentifiable.type](
    title: Option[String],
    locations: List[LocationDeprecated]
  ): Item[Id] =
    Item(Unidentifiable, title, locations)
}
