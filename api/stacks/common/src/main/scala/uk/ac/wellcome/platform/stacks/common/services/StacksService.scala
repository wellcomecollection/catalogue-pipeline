package uk.ac.wellcome.platform.stacks.common.services

import java.time.Instant

import cats.instances.future._
import cats.instances.list._
import cats.syntax.traverse._
import grizzled.slf4j.Logging
import uk.ac.wellcome.platform.stacks.common.models._

import scala.concurrent.{ExecutionContext, Future}

class StacksService(
  catalogueService: CatalogueService,
  sierraService: SierraService
)(
  implicit ec: ExecutionContext
) extends Logging {

  def requestHoldOnItem(
    userIdentifier: StacksUserIdentifier,
    catalogueItemId: CatalogueItemIdentifier,
    neededBy: Option[Instant]
  ): Future[HoldResponse] =
    for {
      stacksItem <- catalogueService.getStacksItem(catalogueItemId)

      response <- stacksItem match {
        case Some(id) =>
          sierraService.placeHold(
            userIdentifier = userIdentifier,
            sierraItemIdentifier = id.sierraId,
            neededBy = neededBy
          )
        case None =>
          Future.failed(
            new Exception(f"Could not locate item $catalogueItemId!")
          )
      }

    } yield response

  def getStacksWork(
    workId: StacksWorkIdentifier
  ): Future[StacksWork] =
    for {
      stacksItemIds <- catalogueService.getAllStacksItems(workId)

      itemStatuses <- stacksItemIds
        .map(_.sierraId)
        .traverse(sierraService.getItemStatus)

      stacksItemsWithStatuses = (stacksItemIds zip itemStatuses) map {
        case (itemId, status) => StacksItem(itemId, status)
      }

    } yield StacksWork(workId, stacksItemsWithStatuses)

  def getStacksUserHolds(
    userId: StacksUserIdentifier
  ): Future[StacksUserHolds] =
    for {
      userHolds <- sierraService.getStacksUserHolds(userId)
      stacksItemIds <- userHolds.holds
        .map(_.itemId)
        .traverse(catalogueService.getStacksItem)

      updatedUserHolds = (userHolds.holds zip stacksItemIds) map {
        case (hold, Some(stacksItemId)) =>
          Some(hold.copy(itemId = stacksItemId))
        case (hold, None) =>
          error(f"Unable to map $hold to Catalogue Id!")
          None
      }

    } yield userHolds.copy(holds = updatedUserHolds.flatten)
}
