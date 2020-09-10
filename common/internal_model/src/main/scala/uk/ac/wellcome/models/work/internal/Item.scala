package uk.ac.wellcome.models.work.internal

case class Item[+DataId](
  id: DataId,
  title: Option[String] = None,
  locations: List[LocationDeprecated] = Nil,
  ontologyType: String = "Item"
) extends HasId[DataId]

object Item {

  def apply[DataId >: Id.Unidentifiable.type](
    title: Option[String],
    locations: List[LocationDeprecated]
  ): Item[DataId] =
    Item(Id.Unidentifiable, title, locations)
}
