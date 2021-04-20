package uk.ac.wellcome.platform.stacks.common.models

import java.time.Instant

case class StacksUserHolds(
  userId: String,
  holds: List[StacksHold]
)

case class StacksHold(
  itemId: ItemIdentifier[_],
  pickup: StacksPickup,
  status: StacksHoldStatus
)

case class StacksHoldStatus(
  id: String,
  label: String
)

case class StacksPickup(
  location: StacksPickupLocation,
  pickUpBy: Option[Instant]
)
