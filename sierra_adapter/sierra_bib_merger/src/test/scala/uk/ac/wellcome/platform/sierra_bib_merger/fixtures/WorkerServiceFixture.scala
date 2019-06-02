package uk.ac.wellcome.platform.sierra_bib_merger.fixtures

import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.platform.sierra_bib_merger.services.{
  SierraBibMergerUpdaterService,
  SierraBibMergerWorkerService
}
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers

trait WorkerServiceFixture extends SierraAdapterHelpers {

  def withWorkerService[R](vhs: SierraVHS,
                           queue: Queue,
                           messageSender: MemoryMessageSender)(
    testWith: TestWith[SierraBibMergerWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      val updaterService = new SierraBibMergerUpdaterService(vhs)

      withSQSStream[NotificationMessage, R](queue) { sqsStream =>
        val workerService = new SierraBibMergerWorkerService(
          sqsStream = sqsStream,
          messageSender = messageSender,
          sierraBibMergerUpdaterService = updaterService
        )

        workerService.run()

        testWith(workerService)
      }
    }
}
