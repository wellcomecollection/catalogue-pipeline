package uk.ac.wellcome.platform.sierra_item_merger.fixtures

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.fixtures.{SNS, SQS}
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.platform.sierra_item_merger.services.{
  SierraItemMergerUpdaterService,
  SierraItemMergerWorkerService
}
import uk.ac.wellcome.sierra_adapter.model.{
  SierraItemRecord,
  SierraTransformable
}
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import uk.ac.wellcome.storage.store.VersionedStore
import scala.concurrent.ExecutionContext.Implicits.global

trait SierraItemMergerFixtures
    extends Akka
    with SNS
    with SQS
    with SierraAdapterHelpers {
  def withSierraUpdaterService[R](
    hybridStore: VersionedStore[String, Int, SierraTransformable])(
    testWith: TestWith[SierraItemMergerUpdaterService, R]): R = {
    val sierraUpdaterService = new SierraItemMergerUpdaterService(
      versionedHybridStore = hybridStore
    )
    testWith(sierraUpdaterService)
  }

  def withSierraWorkerService[R](
    queue: Queue,
    itemRecordStore: VersionedStore[String, Int, SierraItemRecord],
    sierraTransformableStore: VersionedStore[String, Int, SierraTransformable])(
    testWith: TestWith[(SierraItemMergerWorkerService[String],
                        MemoryMessageSender),
                       R]): R =
    withSierraUpdaterService(sierraTransformableStore) { updaterService =>
      withActorSystem { implicit actorSystem =>
        withSQSStream[NotificationMessage, R](queue) { sqsStream =>
          val snsWriter = new MemoryMessageSender
          val workerService = new SierraItemMergerWorkerService(
            sqsStream = sqsStream,
            sierraItemMergerUpdaterService = updaterService,
            itemRecordStore = itemRecordStore,
            messageSender = snsWriter
          )

          testWith((workerService, snsWriter))
        }
      }
    }

}
