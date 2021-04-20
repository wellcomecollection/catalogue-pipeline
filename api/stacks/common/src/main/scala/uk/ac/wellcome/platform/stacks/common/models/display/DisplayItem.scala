package uk.ac.wellcome.platform.stacks.common.models.display

case class DisplayItem(
  id: String,
  status: Option[DisplayItemStatus] = None,
  `type`: String = "Item"
)

case class DisplayItemStatus(
  id: String,
  label: String,
  `type`: String = "ItemStatus"
)
