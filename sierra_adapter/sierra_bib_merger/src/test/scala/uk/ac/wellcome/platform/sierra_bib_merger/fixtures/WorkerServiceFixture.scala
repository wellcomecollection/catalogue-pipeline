package uk.ac.wellcome.platform.sierra_bib_merger.fixtures

import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.fixtures.{SNS, SQS}
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.platform.sierra_bib_merger.services.{
  SierraBibMergerUpdaterService,
  SierraBibMergerWorkerService
}
import uk.ac.wellcome.sierra_adapter.model.SierraTransformable
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import uk.ac.wellcome.storage.store.VersionedStore

trait WorkerServiceFixture
    extends Akka
    with SierraAdapterHelpers
    with SNS
    with SQS {
  def withWorkerService[R](
    store: VersionedStore[String, Int, SierraTransformable],
    queue: Queue)(testWith: TestWith[(SierraBibMergerWorkerService[String],
                                      MemoryMessageSender),
                                     R]): R =
    withActorSystem { implicit actorSystem =>
      val updaterService = new SierraBibMergerUpdaterService(
        versionedHybridStore = store
      )

      withSQSStream[NotificationMessage, R](queue) { sqsStream =>
        val messageSender = new MemoryMessageSender
        val workerService = new SierraBibMergerWorkerService(
          sqsStream = sqsStream,
          messageSender = messageSender,
          sierraBibMergerUpdaterService = updaterService
        )

        workerService.run()

        testWith((workerService, messageSender))
      }
    }

}
