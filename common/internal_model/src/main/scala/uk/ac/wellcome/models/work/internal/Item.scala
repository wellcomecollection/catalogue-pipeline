package uk.ac.wellcome.models.work.internal

case class Item[+State](
  id: State,
  title: Option[String] = None,
  locationsDeprecated: List[LocationDeprecated] = Nil,
  ontologyType: String = "Item"
) extends HasId[State]

object Item {

  def apply[State >: IdState.Unidentifiable.type](
    title: Option[String],
    locationsDeprecated: List[LocationDeprecated]
  ): Item[State] =
    Item(IdState.Unidentifiable, title, locationsDeprecated)
}
