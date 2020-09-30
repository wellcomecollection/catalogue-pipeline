package uk.ac.wellcome.models.work.internal

case class Item[+State](
  id: State,
  title: Option[String] = None,
  locations: List[LocationDeprecated] = Nil
) extends HasId[State]

object Item {

  def apply[State >: IdState.Unidentifiable.type](
    title: Option[String],
    locations: List[LocationDeprecated]
  ): Item[State] =
    Item(IdState.Unidentifiable, title, locations)
}
