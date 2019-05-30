package uk.ac.wellcome.platform.sierra_item_merger.fixtures

import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.transformable.sierra.SierraItemRecord
import uk.ac.wellcome.platform.sierra_item_merger.services.{SierraItemMergerUpdaterService, SierraItemMergerWorkerService}
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import uk.ac.wellcome.storage.memory.MemoryObjectStore
import uk.ac.wellcome.storage.streaming.CodecInstances._
import uk.ac.wellcome.storage.vhs.{EmptyMetadata, VersionedHybridStore}

import scala.concurrent.ExecutionContext.Implicits.global

trait SierraItemMergerFixtures extends SQS with SierraAdapterHelpers {
  type SierraItemStore = MemoryObjectStore[SierraItemRecord]
  type SierraItemVHS = VersionedHybridStore[String, SierraItemRecord, EmptyMetadata]

  def createItemStore: SierraItemStore = new SierraItemStore()

  def createItemVhs(dao: SierraDao = createDao, store: SierraItemStore): SierraItemVHS =
    new SierraItemVHS {
      override protected val versionedDao: SierraDao = dao
      override protected val objectStore: SierraItemStore = store
    }

  def withSierraWorkerService[R](
    queue: Queue,
    messageSender: MemoryMessageSender,
    itemStore: SierraItemStore,
    vhs: SierraVHS)(testWith: TestWith[SierraItemMergerWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { sqsStream =>
        val updaterService = new SierraItemMergerUpdaterService(vhs)

        val workerService = new SierraItemMergerWorkerService(
          sqsStream = sqsStream,
          sierraItemMergerUpdaterService = updaterService,
          itemStore = itemStore,
          messageSender = messageSender
        )

        testWith(workerService)
      }
    }
}
