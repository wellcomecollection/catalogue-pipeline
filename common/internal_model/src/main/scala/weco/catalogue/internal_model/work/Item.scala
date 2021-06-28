package weco.catalogue.internal_model.work

import weco.catalogue.internal_model.identifiers.{HasId, IdState}
import weco.catalogue.internal_model.locations.Location

case class Item[+State](
  id: State,
  title: Option[String] = None,
  note: Option[String] = None,
  locations: List[Location] = Nil
) extends HasId[State]

object Item {

  def apply[State >: IdState.Unidentifiable.type](
    title: Option[String],
    locations: List[Location]
  ): Item[State] =
    Item(
      id = IdState.Unidentifiable,
      title = title,
      note = None,
      locations = locations
    )
}
