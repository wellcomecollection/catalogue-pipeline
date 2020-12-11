package uk.ac.wellcome.models.work.internal

case class DerivedWorkData(
  availableOnline: Boolean
)

object DerivedWorkData {
  def none: DerivedWorkData = DerivedWorkData(availableOnline = false)

  def apply(workData: WorkData[_]): DerivedWorkData =
    DerivedWorkData(
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
