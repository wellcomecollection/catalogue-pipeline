package uk.ac.wellcome.models.work.internal

case class DerivedData(availableOnline: Boolean)

object DerivedData {
  def none: DerivedData = DerivedData(availableOnline = false)

  def apply(workData: WorkData[_]): DerivedData =
    DerivedData(
      availableOnline = containsDigitalLocation(workData.items)
    )

  private def containsDigitalLocation(items: List[Item[_]]): Boolean =
    items.exists { item =>
      item.locations.exists {
        case _: DigitalLocationDeprecated => true
        case _                            => false
      }
    }
}
