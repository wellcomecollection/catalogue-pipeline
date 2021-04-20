package uk.ac.wellcome.platform.stacks.common.services

import java.time.Instant

import grizzled.slf4j.Logging
import uk.ac.wellcome.platform.stacks.common.models._
import uk.ac.wellcome.platform.stacks.common.services.source.SierraSource

import scala.concurrent.{ExecutionContext, Future}

sealed trait HoldResponse {
  val lastModified: Instant
}
case class HoldAccepted(lastModified: Instant = Instant.now())
    extends HoldResponse
case class HoldRejected(lastModified: Instant = Instant.now())
    extends HoldResponse

class SierraService(
  sierraSource: SierraSource
)(implicit ec: ExecutionContext)
    extends Logging {

  import SierraSource._

  def getItemStatus(
    sierraId: SierraItemIdentifier
  ): Future[StacksItemStatus] =
    sierraSource
      .getSierraItemStub(sierraId)
      .map(item => StacksItemStatus(item.status.code))

  def placeHold(
    userIdentifier: StacksUserIdentifier,
    sierraItemIdentifier: SierraItemIdentifier,
    neededBy: Option[Instant]
  ): Future[HoldResponse] =
    sierraSource
      .postHold(
        userIdentifier,
        sierraItemIdentifier,
        neededBy
      ) map {
      // This is an "XCirc/Record not available" error
      // See https://techdocs.iii.com/sierraapi/Content/zReference/errorHandling.htm
      case Left(SierraErrorCode(132, 2, 500, _, _)) => HoldRejected()

      case Left(result) =>
        warn(s"Unrecognised hold error: $result")
        HoldRejected()

      case Right(_) => HoldAccepted()
    }

  protected def buildStacksHold(
    entry: SierraUserHoldsEntryStub
  ): StacksHold = {

    val itemId = SierraItemIdentifier
      .createFromSierraId(entry.record)

    val pickupLocation = StacksPickupLocation(
      id = entry.pickupLocation.code,
      label = entry.pickupLocation.name
    )

    val pickup = StacksPickup(
      location = pickupLocation,
      pickUpBy = entry.pickupByDate
    )

    val status = StacksHoldStatus(
      id = entry.status.code,
      label = entry.status.name
    )

    StacksHold(itemId, pickup, status)
  }

  def getStacksUserHolds(
    userId: StacksUserIdentifier
  ): Future[StacksUserHolds] = {
    sierraSource
      .getSierraUserHoldsStub(userId)
      .map { hold =>
        StacksUserHolds(
          userId = userId.value,
          holds = hold.entries.map(buildStacksHold)
        )
      }
  }
}
