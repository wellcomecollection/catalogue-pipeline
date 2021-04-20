package uk.ac.wellcome.platform.stacks.common.models.display

import uk.ac.wellcome.platform.stacks.common.models.StacksWork

object DisplayStacksWork {
  def apply(stacksWork: StacksWork): DisplayStacksWork =
    DisplayStacksWork(
      id = stacksWork.id.value,
      items = stacksWork.items.map { stacksItem =>
        DisplayItem(
          id = stacksItem.id.value,
          status = Some(
            DisplayItemStatus(
              id = stacksItem.status.id,
              label = stacksItem.status.label
            )
          )
        )
      }
    )
}

case class DisplayStacksWork(
  id: String,
  items: List[DisplayItem],
  `type`: String = "Work"
)
