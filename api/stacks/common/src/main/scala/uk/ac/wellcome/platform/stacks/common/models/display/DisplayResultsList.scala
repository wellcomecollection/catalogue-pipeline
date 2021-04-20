package uk.ac.wellcome.platform.stacks.common.models.display

import java.time.Instant

import uk.ac.wellcome.platform.stacks.common.models._

object DisplayResultsList {
  def apply(
    stacksUserHolds: StacksUserHolds
  ): DisplayResultsList =
    DisplayResultsList(
      results = stacksUserHolds.holds.map(DisplayRequest(_)),
      totalResults = stacksUserHolds.holds.size
    )
}

case class DisplayResultsList(
  results: List[DisplayRequest],
  totalResults: Int,
  `type`: String = "ResultList"
)

object DisplayRequest {
  def apply(request: StacksHold): DisplayRequest =
    DisplayRequest(
      item = DisplayItem(
        id = request.itemId.value.toString
      ),
      pickupDate = request.pickup.pickUpBy,
      pickupLocation = DisplayLocationDescription(
        request.pickup.location
      ),
      status = DisplayRequestStatus(
        request.status
      )
    )
}

case class DisplayRequest(
  item: DisplayItem,
  pickupDate: Option[Instant],
  pickupLocation: DisplayLocationDescription,
  status: DisplayRequestStatus,
  `type`: String = "Request"
)

object DisplayLocationDescription {
  def apply(location: StacksPickupLocation): DisplayLocationDescription =
    DisplayLocationDescription(
      id = location.id,
      label = location.label
    )
}

case class DisplayLocationDescription(
  id: String,
  label: String,
  `type`: String = "LocationDescription"
)

object DisplayRequestStatus {
  def apply(holdStatus: StacksHoldStatus): DisplayRequestStatus =
    DisplayRequestStatus(
      id = holdStatus.id,
      label = holdStatus.label
    )
}

case class DisplayRequestStatus(
  id: String,
  label: String,
  `type`: String = "RequestStatus"
)
