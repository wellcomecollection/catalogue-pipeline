package weco.catalogue.source_model.sierra.rules

import weco.catalogue.internal_model.locations.{AccessCondition, AccessStatus, ItemStatus, PhysicalLocationType}
import weco.catalogue.source_model.sierra.SierraItemData

/** There are multiple sources of truth for item information in Sierra, and whether
  * a given item can be requested online.
  *
  * This object tries to create a single, consistent view of this data.
  * It returns two values:
  *
  *   - An access condition that can be added to a location on an Item.
  *     This would be set in the Catalogue API.
  *   - An ItemStatus that returns a simpler "is this available right now".
  *     This would be returned from the items API with the most up-to-date
  *     data from Sierra.
  *
  */
object SierraItemAccess {
  def apply(
    bibStatus: Option[AccessStatus],
    location: Option[PhysicalLocationType],
    itemData: SierraItemData
  ): (Option[AccessCondition], ItemStatus) =
    ???
}
